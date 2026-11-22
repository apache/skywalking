/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.core.config.v2.compiler;

import java.util.Collections;
import java.util.Map;
import java.util.HashMap;
import java.util.function.BiFunction;
import javassist.ClassPool;
import org.apache.skywalking.oap.server.core.query.type.Service;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HierarchyRuleClassGeneratorTest {

    private HierarchyRuleClassGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new HierarchyRuleClassGenerator(new ClassPool(true));
    }

    @Test
    void aTextJoinsOnlyOntoATextANumberOrACharacter() {
        for (final String join : new String[] {"u.layers + 'x'", "'x' + u.layers"}) {
            final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> generator.compile("join", "{ (u, l) -> " + join + " == l.name }"));
            assertTrue(e.getMessage().contains("a text +") || e.getMessage().contains("+ a string has no meaning"),
                e.getMessage());
        }
    }

    @Test
    void minusOnAStringIsRefused() {
        // Groovy's String - String removes the text, which Java has no - for
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> generator.compile("minus", "{ (u, l) -> u.name - '-svc' == l.name }"));
        assertTrue(e.getMessage().contains("- on a string"), e.getMessage());
    }

    @Test
    void compileSimpleNameEquality() throws Exception {
        final BiFunction<Service, Service, Boolean> fn = generator.compile(
            "name", "{ (u, l) -> u.name == l.name }");

        assertNotNull(fn);

        final Service upper = new Service();
        upper.setName("svc-a");
        final Service lower = new Service();
        lower.setName("svc-a");
        assertTrue(fn.apply(upper, lower));

        lower.setName("svc-b");
        assertFalse(fn.apply(upper, lower));
    }

    @Test
    void compileShortNameEquality() throws Exception {
        final BiFunction<Service, Service, Boolean> fn = generator.compile(
            "short-name", "{ (u, l) -> u.shortName == l.shortName }");

        assertNotNull(fn);

        final Service upper = new Service();
        upper.setShortName("svc");
        final Service lower = new Service();
        lower.setShortName("svc");
        assertTrue(fn.apply(upper, lower));

        lower.setShortName("other");
        assertFalse(fn.apply(upper, lower));
    }

    @Test
    void compileLowerShortNameRemoveNs() throws Exception {
        final String expr = "{ (u, l) -> {"
            + " if (l.shortName.lastIndexOf('.') > 0) {"
            + "   return u.shortName == l.shortName.substring(0, l.shortName.lastIndexOf('.'));"
            + " }"
            + " return false;"
            + "} }";
        final BiFunction<Service, Service, Boolean> fn = generator.compile(
            "lower-short-name-remove-ns", expr);

        assertNotNull(fn);

        final Service upper = new Service();
        upper.setShortName("svc-a");
        final Service lower = new Service();
        lower.setShortName("svc-a.ns1");
        assertTrue(fn.apply(upper, lower));

        lower.setShortName("svc-b.ns1");
        assertFalse(fn.apply(upper, lower));

        lower.setShortName("no-dot");
        assertFalse(fn.apply(upper, lower));
    }

    @Test
    void compileLowerShortNameWithFqdn() throws Exception {
        final String expr = "{ (u, l) -> {"
            + " if (u.shortName.lastIndexOf(':') > 0) {"
            + "   return u.shortName.substring(0, u.shortName.lastIndexOf(':'))"
            + "     == l.shortName.concat('.svc.cluster.local');"
            + " }"
            + " return false;"
            + "} }";
        final BiFunction<Service, Service, Boolean> fn = generator.compile(
            "lower-short-name-with-fqdn", expr);

        assertNotNull(fn);

        final Service upper = new Service();
        upper.setShortName("svc-a.svc.cluster.local:8080");
        final Service lower = new Service();
        lower.setShortName("svc-a");
        assertTrue(fn.apply(upper, lower));

        upper.setShortName("no-port");
        assertFalse(fn.apply(upper, lower));
    }

    // ==================== Error handling tests ====================

    @Test
    void emptyExpressionThrows() {
        // Demo error: Hierarchy rule parsing failed: 1:0 mismatched input '<EOF>'
        //   expecting '{'
        assertThrows(Exception.class,
            () -> generator.compile("empty", ""));
    }

    @Test
    void missingClosureBracesThrows() {
        // Demo error: Hierarchy rule parsing failed: 1:0 mismatched input 'u'
        //   expecting '{'
        assertThrows(Exception.class,
            () -> generator.compile("test", "u.name == l.name"));
    }

    @Test
    void missingParametersThrows() {
        // Demo error: Hierarchy rule parsing failed: 1:2 mismatched input '}'
        //   expecting '('
        assertThrows(Exception.class,
            () -> generator.compile("test", "{ }"));
    }

    @Test
    void invalidFieldAccessThrows() {
        // Demo error: Service.getNonExistent(...) is not supported in a hierarchy rule. A Service allows
        //   [equals, toString] and its getters (see the service hierarchy documentation)
        assertThrows(Exception.class,
            () -> generator.compile("test",
                "{ (u, l) -> u.nonExistent == l.nonExistent }"));
    }

    // ==================== Groovy semantics kept, or rejected ====================

    @Test
    void elseIfChainRunsEveryBranch() throws Exception {
        final BiFunction<Service, Service, Boolean> fn = generator.compile("else-if",
            "{ (u, l) -> { if (u.name == 'a') return false; else if (u.name == 'b') return false;"
                + " else return true; } }");
        assertFalse(fn.apply(named("a"), named("x")));
        assertFalse(fn.apply(named("b"), named("x")));
        assertTrue(fn.apply(named("c"), named("x")));
    }

    @Test
    void elseIfWithoutFinalElseFallsThroughToTheNextStatement() throws Exception {
        final BiFunction<Service, Service, Boolean> fn = generator.compile("else-if-no-else",
            "{ (u, l) -> { if (u.name == 'a') return true; else if (u.name == 'b') return true;"
                + " return false; } }");
        assertTrue(fn.apply(named("a"), named("x")));
        assertTrue(fn.apply(named("b"), named("x")));
        assertFalse(fn.apply(named("c"), named("x")));
    }

    @Test
    void thenAndElseKeepTheirOwnBranches() throws Exception {
        final BiFunction<Service, Service, Boolean> fn = generator.compile("then-else",
            "{ (u, l) -> { if (u.name == l.name) { return true; } else return false; return false; } }");
        assertTrue(fn.apply(named("a"), named("a")));
        assertFalse(fn.apply(named("a"), named("b")));
    }

    @Test
    void aStringIsTheTextBetweenItsQuotes() throws Exception {
        // as hierarchy rules have always read a string: its escapes stay as written
        final BiFunction<Service, Service, Boolean> fn = generator.compile("escape",
            "{ (u, l) -> u.name == 'it\\'s' }");
        assertTrue(fn.apply(named("it\\'s"), named("x")));
        assertTrue(generator.compile("path", "{ (u, l) -> u.name == 'C:\\temp' }").apply(named("C:\\temp"), named("x")));
    }

    @Test
    void bareReturnValueUsesGroovyTruth() throws Exception {
        final BiFunction<Service, Service, Boolean> name = generator.compile("return-name",
            "{ (u, l) -> { return u.name; } }");
        assertFalse(name.apply(named(""), named("x")));
        assertFalse(name.apply(named(null), named("x")));
        assertTrue(name.apply(named("svc"), named("x")));

        final BiFunction<Service, Service, Boolean> length = generator.compile("return-length",
            "{ (u, l) -> { return u.name.length(); } }");
        assertFalse(length.apply(named(""), named("x")));
        assertTrue(length.apply(named("svc"), named("x")));

        final BiFunction<Service, Service, Boolean> layers = generator.compile("return-layers",
            "{ (u, l) -> { return u.layers; } }");
        final Service withLayer = named("svc");
        withLayer.setLayers(Collections.singleton("GENERAL"));
        assertFalse(layers.apply(named("svc"), named("x")));
        assertTrue(layers.apply(withLayer, named("x")));
    }

    @Test
    void bareConditionUsesGroovyTruth() throws Exception {
        final BiFunction<Service, Service, Boolean> fn = generator.compile("bare-condition",
            "{ (u, l) -> { if (u.name && !l.name) return true; return false; } }");
        assertTrue(fn.apply(named("a"), named("")));
        assertTrue(fn.apply(named("a"), named(null)));
        assertFalse(fn.apply(named(""), named("")));
        assertFalse(fn.apply(named("a"), named("b")));
    }

    @Test
    void greaterOrEqualAndLessOrEqual() throws Exception {
        final BiFunction<Service, Service, Boolean> gte = generator.compile("gte",
            "{ (u, l) -> { if (l.shortName.lastIndexOf('.') >= 1) return true; return false; } }");
        assertTrue(gte.apply(named("x"), shortNamed("a.b")));
        assertFalse(gte.apply(named("x"), shortNamed(".b")));

        final BiFunction<Service, Service, Boolean> lte = generator.compile("lte",
            "{ (u, l) -> { if (l.shortName.lastIndexOf('.') <= 0) return true; return false; } }");
        assertTrue(lte.apply(named("x"), shortNamed(".b")));
        assertTrue(lte.apply(named("x"), shortNamed("no-dot")));
        assertFalse(lte.apply(named("x"), shortNamed("a.b")));
    }

    @Test
    void notBindsTighterThanAnd() throws Exception {
        final BiFunction<Service, Service, Boolean> fn = generator.compile("not-and",
            "{ (u, l) -> { if (!u.name.isEmpty() && l.name.isEmpty()) return true; return false; } }");
        // (!true) && false, where !(true && false) would be true
        assertFalse(fn.apply(named(""), named("x")));
        assertTrue(fn.apply(named("a"), named("")));
    }

    @Test
    void subtractionThenAdditionAssociatesLeft() throws Exception {
        final BiFunction<Service, Service, Boolean> fn = generator.compile("arith",
            "{ (u, l) -> u.name == l.name.substring(5 - 2 + 1) }");
        assertTrue(fn.apply(named("efgh"), named("abcdefgh")));
        assertFalse(fn.apply(named("cdefgh"), named("abcdefgh")));
    }

    @Test
    void anIntegerBeyondTheIntRangeKeepsItsValue() throws Exception {
        // As an int, 4294967296 wraps to 0.
        final BiFunction<Service, Service, Boolean> fn = generator.compile("long-literal",
            "{ (u, l) -> { if (u.name.length() < 4294967296) return true; return false; } }");
        assertTrue(fn.apply(named("svc"), named("x")));
    }

    @Test
    void equalityComparesAsGroovy() throws Exception {
        final BiFunction<Service, Service, Boolean> empty = generator.compile("int-equality",
            "{ (u, l) -> { if (u.name.length() == 0) return true; return false; } }");
        assertTrue(empty.apply(named(""), named("x")));
        assertFalse(empty.apply(named("a"), named("x")));
        final BiFunction<Service, Service, Boolean> chars = generator.compile("array-equality",
            "{ (u, l) -> u.name.split('') == l.name.split('') }");
        assertTrue(chars.apply(named("ab"), named("ab")));
        assertFalse(chars.apply(named("ab"), named("ba")));
        final BiFunction<Service, Service, Boolean> charCode = generator.compile("char-code",
            "{ (u, l) -> { if (u.name.charAt(0) == 1) return true; return false; } }");
        assertTrue(charCode.apply(named("1"), named("x")), "a character is the one-character string it is: '1' == 1");
        assertFalse(charCode.apply(named("A"), named("x")));
    }

    @Test
    void aLeadingZeroIntegerIsOctal() throws Exception {
        final BiFunction<Service, Service, Boolean> octal = generator.compile("octal",
            "{ (u, l) -> { if (u.name.length() == 010) return true; return false; } }");
        assertTrue(octal.apply(named("abcdefgh"), named("x")), "010 is octal 8, as Groovy");
        assertThrows(IllegalArgumentException.class, () -> generator.compile("octal",
            "{ (u, l) -> { if (u.name.length() == 08) return true; return false; } }"));
    }

    @Test
    void aRefusedCallStopsTheBootNamingTheRule() {
        final Map<String, String> rules = new HashMap<>();
        rules.put("abs", "{ (u, l) -> Math.abs(u.name.length()) == l.name.length() }");
        final Map<String, Integer> lines = new HashMap<>();
        lines.put("abs", 7);
        final IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> new CompiledHierarchyRuleProvider().buildRules(rules, lines));
        assertTrue(e.getMessage().contains("abs") && e.getMessage().contains(":7)"), e.getMessage());
        assertTrue(e.getMessage().contains("not supported in a hierarchy rule"), e.getMessage());
    }

    @Test
    void aFailedRuleNamesItsExpressionOnce() {
        final String expression = "{ (u, l) -> { if (u.name == l.name) return true; } }";
        final IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> new CompiledHierarchyRuleProvider().buildRules(
                Collections.singletonMap("no-return", expression), Collections.singletonMap("no-return", 3)));
        assertTrue(e.getMessage().contains("without a return"), e.getMessage());
        assertEquals(e.getMessage().indexOf(expression), e.getMessage().lastIndexOf(expression), e.getMessage());
    }

    private static Service group(final Service service, final String group) {
        service.setGroup(group);
        return service;
    }

    @Test
    void onlyTheDocumentedCallsCompile() throws Exception {
        assertTrue(generator.compile("supported", "{ (u, l) -> { if (u.name.toLowerCase().startsWith(l.name.trim()) "
            + "&& Integer.parseInt(u.group) > 0) return true; return false; } }")
            .apply(group(named("Abc"), "1"), named(" ab ")));
        final Service a = named("a-b");
        assertTrue(generator.compile("text", "{ (u, l) -> u.name.split('-').toString() == l.name }")
            .apply(a, named("[a, b]")), "an array prints as Groovy prints it");
        assertTrue(generator.compile("unbox", "{ (u, l) -> u.name.substring(Integer.valueOf(l.group)) == 'b' }")
            .apply(a, group(named("x"), "2")), "a wrapper unboxed into an int parameter");
        assertTrue(generator.compile("boxed", "{ (u, l) -> u.name.length().toString() == l.name }")
            .apply(named("abc"), named("3")), "a primitive receiver is its box");
        assertThrows(IllegalArgumentException.class, () -> generator.compile("boolOrder",
            "{ (u, l) -> { if (u.normal > 0) return true; return false; } }"), "a boolean field in an ordering");
        final IllegalArgumentException boolText = assertThrows(IllegalArgumentException.class,
            () -> generator.compile("boolText", "{ (u, l) -> { if (u.normal + '-svc' == l.name) return true; "
                + "return false; } }"), "a boolean field + a string");
        assertTrue(boolText.getMessage().contains("boolean"), boolText.getMessage());
        for (final String refused : new String[] {"u.name.bytes", "u.layers.stream()", "u.name.toCharArray()",
            "Double.parseDouble(u.name)", "Integer.getInteger(u.name)", "Math.abs(1)", "u.name.getBytes()"}) {
            final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> generator.compile("refused", "{ (u, l) -> " + refused + " == l.name }"), refused);
            assertTrue(e.getMessage().contains("not supported in a hierarchy rule"), refused + ": " + e.getMessage());
        }
    }

    @Test
    void anInterfaceValueAnswersObjectsMethodsAndOverloadsFollowTheArguments() throws Exception {
        assertTrue(generator.compile("layers", "{ (u, l) -> u.layers.toString() == l.layers.toString() }")
            .apply(named("a"), named("b")), "a Set prints as Java prints it");
        assertTrue(generator.compile("indexOf", "{ (u, l) -> u.name.indexOf(Integer.valueOf('98')) == 1 }")
            .apply(named("abc"), named("x")), "indexOf(int), the wrapper unboxed");
        assertTrue(generator.compile("widen", "{ (u, l) -> Long.valueOf(1) == u.name.length() }")
            .apply(named("a"), named("b")), "an int literal widened to valueOf(long)");
        final IllegalArgumentException codeOrder = assertThrows(IllegalArgumentException.class,
            () -> generator.compile("codeOrder", "{ (u, l) -> { if (u.name.charAt(0) + 1 < 'abc') return true; "
                + "return false; } }"), "a number against a text that is no number");
        assertTrue(codeOrder.getMessage().contains("abc"), codeOrder.getMessage());
        // a $ that starts no interpolation is the character
        assertTrue(generator.compile("dollar", "{ (u, l) -> u.name.replaceAll(\"(a)-(b)\", \"$2/$1\") == l.name }")
            .apply(named("a-b"), named("b/a")));
        // toString() on a null fails the rule, as it always did
        final Service noLayers = named("a");
        noLayers.setLayers(null);
        assertThrows(NullPointerException.class, () -> generator.compile("nullText",
            "{ (u, l) -> u.layers.toString() == 'null' }").apply(noLayers, named("x")));
        assertTrue(generator.compile("unboxWiden", "{ (u, l) -> Long.valueOf(Integer.valueOf('1')) == 1 }")
            .apply(named("a"), named("b")), "unboxed, then widened to valueOf(long)");
        for (final String narrowed : new String[] {"u.name.substring(Long.parseLong('4294967297')) == 'bc'",
            "u.name.substring(true) == 'bc'"}) {
            // no overload takes the argument: never narrowed to one
            assertThrows(Exception.class, () -> generator.compile("narrowed", "{ (u, l) -> " + narrowed + " }")
                .apply(named("abc"), named("x")), narrowed);
        }
        for (final String refused : new String[] {"arg0.toString() == l.name", "x.name == l.name"}) {
            final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> generator.compile("unknown", "{ (u, l) -> " + refused + " }"), refused);
            assertTrue(e.getMessage().contains("not supported in a hierarchy rule"), refused + ": " + e.getMessage());
        }
    }

    @Test
    void parametersNamedLikeTheGeneratedMethodsOwnKeepTheirServices() throws Exception {
        final BiFunction<Service, Service, Boolean> fn = generator.compile("arg-names",
            "{ (arg1, arg0) -> arg1.name == arg0.name.substring(1) }");
        assertTrue(fn.apply(named("ow"), named("low")));
        assertFalse(fn.apply(named("low"), named("ow")));
        final BiFunction<Service, Service, Boolean> upperName = generator.compile("upperName",
            "{ (Upper, Lower) -> { if (Upper.shortName.charAt(0) >= 4) return true; return false; } }");
        assertFalse(upperName.apply(shortNamed("3x"), named("pod")), "a capitalized parameter is still the Service");
    }

    @Test
    void aBoxedNumberAddsAndComparesAsANumber() throws Exception {
        final BiFunction<Service, Service, Boolean> next = generator.compile("tier", "{ (u, l) -> "
            + "Integer.valueOf(u.shortName.substring(5)) + 1 == Integer.parseInt(l.shortName.substring(5)) }");
        final Service upper = new Service();
        upper.setShortName("tier-2");
        final Service lower = new Service();
        lower.setShortName("tier-3");
        assertTrue(next.apply(upper, lower));
        lower.setShortName("tier-21");
        assertFalse(next.apply(upper, lower));
        final BiFunction<Service, Service, Boolean> above = generator.compile("above", "{ (u, l) -> { if ("
            + "Integer.valueOf(u.shortName.substring(5)) > Integer.valueOf(l.shortName.substring(5))) return true; "
            + "return false; } }");
        upper.setShortName("tier-9");
        lower.setShortName("tier-10");
        assertFalse(above.apply(upper, lower));
    }

    @Test
    void aCharacterOrNumericStringOrdersAsTheNumberItReads() throws Exception {
        final BiFunction<Service, Service, Boolean> digit = generator.compile("char",
            "{ (u, l) -> { if (u.name.charAt(0) > 4) return true; return false; } }");
        assertTrue(digit.apply(named("5x"), named("x")));
        final BiFunction<Service, Service, Boolean> numeric = generator.compile("numeric",
            "{ (u, l) -> { if (u.shortName > 5) return true; return false; } }");
        final Service upper = new Service();
        upper.setShortName("10");
        assertTrue(numeric.apply(upper, upper), "the string 10 reads as a number");
        final IllegalArgumentException noNumber = assertThrows(IllegalArgumentException.class,
            () -> numeric.apply(shortNamed("abc"), named("x")));
        assertTrue(noNumber.getMessage().contains("reads as no number"), noNumber.getMessage());
        final BiFunction<Service, Service, Boolean> initials = generator.compile("initials",
            "{ (u, l) -> { if (u.shortName.charAt(0) < l.shortName.charAt(0)) return true; return false; } }");
        assertTrue(initials.apply(shortNamed("api"), shortNamed("web")), "two characters order as text");
    }

    @Test
    void orderingRefusesBooleansAndNonNumericLiterals() {
        assertThrows(IllegalArgumentException.class, () -> generator.compile("bool",
            "{ (u, l) -> { if (u.name.startsWith('internal') >= 0) return true; return false; } }"));
        assertThrows(IllegalArgumentException.class, () -> generator.compile("literal",
            "{ (u, l) -> { if (u.name.length() > 'prod') return true; return false; } }"));
        assertThrows(IllegalArgumentException.class, () -> generator.compile("literal2",
            "{ (u, l) -> { if (u.name.length() + 1 > 'prod') return true; return false; } }"));
        assertThrows(IllegalArgumentException.class, () -> generator.compile("boolLiteral",
            "{ (u, l) -> { if (u.name.length() >= true) return true; return false; } }"));
        assertThrows(IllegalArgumentException.class, () -> generator.compile("boolArithmetic",
            "{ (u, l) -> { if (u.name.startsWith('svc') + 1 >= 2) return true; return false; } }"));
    }

    @Test
    void nullOrdersLowest() throws Exception {
        final BiFunction<Service, Service, Boolean> group = generator.compile("group",
            "{ (u, l) -> { if (u.group < l.name) return true; return false; } }");
        assertTrue(group.apply(named("svc"), named("pod")), "null orders below everything, as in Groovy");
    }

    private static Service named(final String name) {
        final Service s = new Service();
        s.setName(name);
        return s;
    }

    private static Service shortNamed(final String shortName) {
        final Service s = new Service();
        s.setShortName(shortName);
        return s;
    }
}
