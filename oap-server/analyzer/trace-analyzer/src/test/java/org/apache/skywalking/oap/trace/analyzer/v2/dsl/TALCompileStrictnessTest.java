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

package org.apache.skywalking.oap.trace.analyzer.v2.dsl;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.core.config.Property;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.apache.skywalking.oap.server.core.dsl.classloader.BytecodeClassDefiner;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.core.source.TALFunctionProvider;
import org.apache.skywalking.oap.server.core.trace.SpanMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.testing.dsl.DslClassOutput;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALClassGenerator;
import org.apache.skywalking.oap.trace.analyzer.v2.compiler.TALFunctionRegistry;
import org.apache.skywalking.oap.trace.analyzer.v2.dsl.spec.FilterSpec;
import org.apache.skywalking.oap.trace.analyzer.v2.provider.TALConfigs;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.OtlpTALSourceTypeProvider;
import org.apache.skywalking.oap.trace.analyzer.v2.spi.SwNativeTALSourceTypeProvider;
import javassist.ClassPool;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A rule either runs as written or does not compile: nothing it says is dropped, replaced by a later statement or
 * read as something else. Each rule writes what it computed into {@code meta.service}, which the test reads.
 */
class TALCompileStrictnessTest {
    private static final String SCRIPT_LOGGER = "org.apache.skywalking.trace.analysis.script.test-strictness";
    private static final String SCOPE = "scope service(\"s\", \"GENERAL\"); function cpm; value 1";

    private TALFunctionRegistry functions;
    private FilterSpec filterSpec;
    private CapturingAppender appender;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        final ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.has(anyString())).thenReturn(false);
        functions = new TALFunctionRegistry(List.of(new TestFunctionProvider(), new FailureFunctions()));
        functions.bind(null, List.of(
            new TestInputProvider(), new OtlpTALSourceTypeProvider(), new SwNativeTALSourceTypeProvider()));
        final ModuleServiceHolder coreProvider = mock(ModuleServiceHolder.class);
        final ModuleProviderHolder coreModule = mock(ModuleProviderHolder.class);
        when(coreModule.provider()).thenReturn(coreProvider);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);
        filterSpec = TALTestModules.filterSpec(moduleManager, coreProvider, mock(MeterSystem.class),
            new NamingControl(70, 70, 150, new EndpointNameGrouping()), mock(SourceReceiver.class));
    }

    @AfterEach
    void tearDown() {
        if (appender != null) {
            ((Logger) LogManager.getLogger(SCRIPT_LOGGER)).removeAppender(appender);
            appender.stop();
            appender = null;
            Configurator.setLevel(SCRIPT_LOGGER, previousLevel);
        }
    }

    @Nested
    class StatementsGivenTwice {
        @Test
        void aMetricsClauseGivenTwiceIsACompileError() {
            assertRejected("filter { extractor { metrics { name \"a\"; name \"b\"; " + SCOPE + " } } }",
                "metrics block sets 'name' more than once");
            assertRejected("filter { extractor { metrics { name \"a\"; " + SCOPE + "; value 2 } } }",
                "metrics block sets 'value' more than once");
        }

        @Test
        void aLabelKeyGivenTwiceIsACompileErrorInOneLabelsClauseOrAcrossTwo() throws Exception {
            final String labeled = "filter { extractor { metrics { name \"l\"; scope service(\"s\", \"GENERAL\"); "
                + "function sumLabeled; value 1; ";
            assertRejected(labeled + "labels k: \"1\", k: \"2\" } } }", "sets the label 'k' more than once");
            assertRejected(labeled + "labels k: \"1\"; labels k: \"2\" } } }", "sets the label 'k' more than once");
            assertNotNull(compile(labeled + "labels k: \"1\"; labels j: \"2\" } } }"),
                "two labels clauses with different keys both apply");
        }

        @Test
        void aTagKeyGivenTwiceInOneStatementIsACompileError() {
            assertRejected("filter { extractor { tag k: \"1\", k: \"2\" } }", "tag sets the key 'k' more than once");
        }
    }

    @Nested
    class Casts {
        @Test
        void eachTagEntryKeepsItsOwnCast() throws Exception {
            // Each entry converts by its own cast alone.
            final ExecutionContext ctx = execute(
                "filter { extractor { tag route: \"007\", retries: \"03\" as Integer } }", "n");
            assertEquals(Map.of("route", "007", "retries", "3"), ctx.getAdditionalTags());
        }

        @Test
        void aTypeAsDoesNotKnowIsACompileErrorWhereverItStands() {
            assertRejected("filter { extractor { if (\"false\" as Bool) { service \"t\" } } }",
                "`as Bool` names no type");
            assertRejected("filter { extractor { service \"\" + twice(\"3.9\" as Doubel) } }",
                "`as Doubel` names no type");
            assertRejected("filter { extractor { service \"\" + ((\"3.9\" as Doubel) + 1) } }",
                "`as Doubel` names no type");
            assertRejected("filter { extractor { def b = \"x\" as Bool\n service b } }", "`as Bool` names no type");
            assertRejected("filter { extractor { if (span.name as java.lang.Strin == \"n\") { service \"t\" } } }",
                "`as java.lang.Strin` names no class on the classpath");
        }

        @Test
        void aClassNameAfterAsIsResolvedWhereverItStands() throws Exception {
            // A literal's cast is resolved too.
            assertRejected("filter { extractor { tag region: \"west\" as java.lang.Strng } }",
                "`as java.lang.Strng` names no class on the classpath");
            assertRejected("filter { extractor { if (meta.service != null as java.lang.Strng) { service \"t\" } } }",
                "`as java.lang.Strng` names no class on the classpath");
            assertEquals(Map.of("region", "west"),
                execute("filter { extractor { tag region: \"west\" as java.lang.String } }", "n").getAdditionalTags());
            // A class whose last name is a scalar type's, and a member class, by the names Java source gives them.
            assertEquals("absent", run("filter { extractor { def n = null as java.lang.Long\n"
                + " if (n == null) { service \"absent\" } } }", "n"));
            assertNotNull(compile("filter { extractor { def e = null as io.opentelemetry.proto.trace.v1.Span.Event\n"
                + " service \"x\" } }"));
        }

        @Test
        void aCastThatFailsFailsTheSpan() throws Exception {
            // A class cast, a Layer or an enum name that is none fails the span.
            final String size = "filter { extractor { def p = parseJsonObject(span.name)\n"
                + " if ((p.get(\"items\") as com.google.gson.JsonArray)?.size() > 0) { service \"yes\" }"
                + " else { service \"no\" } } }";
            assertEquals("yes", run(size, "{\"items\": [1]}"));
            assertThrows(ClassCastException.class, () -> run(size, "{\"items\": 1}"));
            assertThrows(IllegalArgumentException.class, () -> run("filter { extractor { if (span.name as Layer) {"
                + " service \"yes\" } else { service \"no\" } } }", "NO_SUCH_LAYER"));
            assertThrows(IllegalArgumentException.class, () -> run("filter { extractor { if (span.name as DetectPoint"
                + " != \"SERVER\") { service \"yes\" } else { service \"no\" } } }", "OTLP"));
            assertEquals("yes", run("filter { extractor { if (span.name as DetectPoint != \"SERVER\") {"
                + " service \"yes\" } else { service \"no\" } } }", "CLIENT"));
            // A literal is checked when the rule compiles, an identity class cast around it included.
            assertRejected("filter { extractor { if ((\"WRONG\" as java.lang.String) as Layer == \"GENERAL\") {"
                + " service \"x\" } } }", "names no registered layer");
        }

        @Test
        void aCharacterCastAsANumberIsACompileError() {
            assertRejected("filter { extractor { service \"\" + (span.name.charAt(0) as Integer) } }",
                "A character cannot be cast as a number");
            assertRejected("filter { extractor { def n = span.name.charAt(0) as Integer\n service \"\" + n } }",
                "A character cannot be cast as a number");
        }

        @Test
        void aCastTrimsATextAndKeepsANull() throws Exception {
            // A cast trims a text, as LAL and Groovy do.
            assertEquals("42", run("filter { extractor { def n = span.name as Long\n service \"\" + n } }", " 42 "));
            // A null stays null through a cast, and fails in arithmetic or as a primitive argument.
            assertEquals("n=null", run("filter { extractor { def n = labels().get(\"missing\") as Integer\n"
                + " service \"n=\" + n } }", "n"));
            assertThrows(IllegalArgumentException.class, () -> run("filter { extractor {"
                + " def n = null as java.lang.Integer\n service \"\" + (n + 1) } }", "n"));
            assertThrows(IllegalArgumentException.class, () -> run("filter { extractor {"
                + " service span.name.substring(labels().get(\"missing\")) } }", "n"));
        }

        @Test
        void aGroupedCastKeepsItsNull() throws Exception {
            assertEquals("fallback", run("filter { extractor { def p = parseJsonObject(span.name)\n"
                + " def n = (p.get(\"optional\") as Integer)\n if (n > 0) { service \"pos\" } else {"
                + " service \"fallback\" } } }", "{}"));
            assertEquals("v=null", run("filter { extractor { service \"v=\" + (span.name as Long)?.toString() } }",
                " "));
        }

        @Test
        void aMapDoesNotConvertToANumber() {
            assertThrows(IllegalArgumentException.class, () -> run("filter { extractor { def n = labels() as Integer\n"
                + " service \"x\" } }", "n"));
        }

        @Test
        void aBlankBooleanIsMissing() throws Exception {
            // null, and false in a condition.
            assertEquals("f", run("filter { extractor { def flag = span.name as Boolean\n if (flag) { service \"t\" }"
                + " else { service \"f\" } } }", " "));
            assertEquals("f", run("filter { extractor { if (span.name as Boolean) { service \"t\" } else {"
                + " service \"f\" } } }", " "));
        }

        @Test
        void conditionsUseGroovyTruthAndAsBooleanConvertsAsLal() throws Exception {
            final String rule = "filter { extractor { if (span.name as boolean) { service \"t\" }"
                + " else { service \"f\" } } }";
            assertEquals("f", run(rule, "false"));
            assertEquals("t", run(rule, "True"));
            assertEquals("t", run(rule, "1"));
            assertEquals("t", run(rule, "-2"));
            assertEquals("f", run(rule, "0"));
            assertEquals("f", run(rule, ""));
            assertThrows(Exception.class, () -> run(rule, "yes"), "yes has no boolean meaning");
            assertThrows(Exception.class, () -> run("filter { extractor { def b = span.name as Boolean\n"
                + " service \"\" + b } }", "yes"));
            assertRejected("filter { extractor { if (span.name as Bool) { service \"t\" } } }",
                "`as Bool` names no type");
            // Zero, NaN and an empty list are false.
            assertEquals("f", run("filter { extractor { def l = emptyList()\n if (l) { service \"t\" } else {"
                + " service \"f\" } } }", "n"));
            assertEquals("f", run("filter { extractor { def n = span.name as Integer\n if (n) { service \"t\" } else {"
                + " service \"f\" } } }", "0"));
            assertEquals("t", run("filter { extractor { if (span.retries) { service \"t\" } else { service \"f\" } } }",
                "n"));
            // A JSON cast that holds keeps its value: 1 as Boolean is true.
            final String flag = "filter { extractor { def p = parseJsonObject(span.name)\n"
                + " if ((p.get(\"flag\") as com.google.gson.JsonPrimitive)?.getAsInt() as Boolean) { service \"t\" }"
                + " else { service \"f\" } } }";
            assertEquals("t", run(flag, "{\"flag\": 1}"));
            assertEquals("f", run(flag, "{\"flag\": 0}"));
        }

        @Test
        void aClassNameCastCoversTheWholeExpression() throws Exception {
            // The cast applies to the whole sum, so the call takes choose(Object).
            assertEquals("o", run("filter { extractor { service choose(\"svc-\" + span.name as java.lang.Object) } }",
                "n"));
            assertEquals("o", run("filter { extractor { def v = \"svc-\" + span.name as java.lang.Object\n"
                + " service choose(v) } }", "n"));
        }

        @Test
        void aClassNameMayHoldAnyKeywordJavaAllows() throws Exception {
            assertNotNull(compile("filter { extractor {"
                + " def f = null as org.apache.skywalking.oap.trace.analyzer.v2.provider.span.PairingFact\n"
                + " service \"x\" } }"));
        }

        @Test
        void aWholeNumberConvertsExactly() throws Exception {
            // `as Long` keeps a whole JSON number's digits.
            assertEquals("later", run("filter { extractor { def p = parseJsonObject(span.name)\n"
                + " if (p.get(\"end\").getAsNumber() as Long > p.get(\"start\").getAsLong()) { service \"later\" }"
                + " else { service \"not\" } } }", "{\"start\": 1700000000000000000, \"end\": 1700000000000000001}"));
            // A fraction truncates, as LAL's `as Long` does.
            assertEquals("later", run("filter { extractor { def p = parseJsonObject(span.name)\n"
                + " if (p.get(\"v\").getAsNumber() as Long > 0) { service \"later\" } else { service \"not\" } } }",
                "{\"v\": 1.5}"));
        }

        @Test
        void aCheckOrCastNoValueCanPassIsACompileError() {
            assertRejected("filter { extractor { tag retries: \"7\" as java.lang.Long } }",
                "a java.lang.String never is one; convert it with as Long");
            assertRejected("filter { extractor { if (span.name as java.lang.Long > 0) { service \"t\" } } }",
                "a java.lang.String never is one; convert it with as Long");
            assertRejected("filter { extractor { service \"\" + span.name.contains(42) } }",
                "String.contains(int) matches no method; convert the arguments with as");
            assertRejected("filter { extractor { def text = \"\"\n service text.copyValueOf(span.bytes) } }",
                "String.copyValueOf(...) is a static method");
            // `as Short` does not parse, so no hint names it.
            final Exception e = assertThrows(IllegalArgumentException.class,
                () -> compile("filter { extractor { tag n: 5 as java.lang.Short } }"));
            assertTrue(messages(e).contains("never is one") && !messages(e).contains("as Short"), messages(e));
        }

        @Test
        void lessCommonCastForms() throws Exception {
            // A package whose first part is a keyword parses, and is then looked up.
            assertRejected("filter { extractor { def d = null as in.acme.telemetry.SpanDetails\n service \"x\" } }",
                "`as in.acme.telemetry.SpanDetails` names no class on the classpath");
            assertRejected("filter { extractor { def p = (true) as DetectPoint\n service \"x\" } }",
                "'true' as DetectPoint is none of its constants");
            // An Object cast compares as a value whose type is known only when the rule runs, as an Object def does.
            assertEquals("eq", run("filter { extractor { if (thing() as java.lang.Object == \"abc\") {"
                + " service \"eq\" } else { service \"ne\" } } }", "n"));
        }
    }

    @Nested
    class Comparisons {
        @Test
        void numbersOfAnyClassCompareAsNumbers() throws Exception {
            // A decimal compares by its exact value.
            final String amount = "parseJsonObject(span.name).get(\"amount\").getAsBigDecimal()";
            assertEquals("gt", run("filter { extractor { if (" + amount + " > 1) { service \"gt\" } else {"
                + " service \"le\" } } }", "{\"amount\": 1.7}"));
            assertEquals("gt", run("filter { extractor { if ((" + amount + " as java.lang.Number) > 1) {"
                + " service \"gt\" } else { service \"le\" } } }", "{\"amount\": 1.7}"));
            assertEquals("le", run("filter { extractor { if (" + amount + " > 1.7) { service \"gt\" }"
                + " else { service \"le\" } } }", "{\"amount\": 1.7}"));
        }

        @Test
        void numbersCompareAsJavasPrimitives() throws Exception {
            // A NaN compares false, -0.0 equal to 0.
            assertEquals("", run("filter { extractor { if (span.name as Double > 400) { service \"yes\" } } }",
                "NaN"));
            assertEquals("eq", run("filter { extractor { if (span.name as Double == 0) { service \"eq\" } else {"
                + " service \"ne\" } } }", "-0.0"));
        }

        @Test
        void aValueOfUnknownTypeComparesAsATypedOne() throws Exception {
            // A NaN false, -0.0 equal to 0.
            final String list = "filter { extractor { def l = emptyList()\n def a = l.add(span.name as Double)\n"
                + " if (l.get(0) %s) { service \"t\" } else { service \"f\" } } }";
            assertEquals("f", run(String.format(list, "> 1.0"), "NaN"));
            assertEquals("t", run(String.format(list, "== 0.0"), "-0.0"));
            assertEquals("f", run("filter { extractor { if (span.name as Double > 1.0) { service \"t\" } else {"
                + " service \"f\" } } }", "NaN"));
        }

        @Test
        void wholeJsonNumbersCompareByTheirDigits() throws Exception {
            // Two nanosecond timestamps one apart.
            final String json = "{\"start\": 1700000000000000000, \"end\": 1700000000000000001}";
            final String rule = "filter { extractor { def p = parseJsonObject(span.name)\n"
                + " if (p.get(\"end\").%s > p.get(\"start\").%s) { service \"later\" } else { service \"not\" } } }";
            assertEquals("later", run(String.format(rule, "getAsNumber()", "getAsNumber()"), json));
            assertEquals("later", run(String.format(rule, "getAsNumber()", "getAsBigInteger()"), json));
            assertEquals("later", run(String.format(rule, "getAsBigInteger()", "getAsLong()"), json));
            assertEquals("not", run(String.format(rule, "getAsNumber()", "getAsNumber()").replace("\"start\").",
                "\"end\")."), json));
        }

        @Test
        void aFloatComparesWithAnotherNumberInDouble() throws Exception {
            // By its double value, 1000000.125, not by its shortest text.
            final String rule = "filter { extractor { def p = parseJsonObject(span.name)\n"
                + " if (p.get(\"f\").getAsFloat() %s p.get(\"b\").getAsBigDecimal()) { service \"t\" }"
                + " else { service \"f\" } } }";
            assertEquals("t", run(String.format(rule, ">"), "{\"f\": 1000000.125, \"b\": 1000000.11}"));
            assertEquals("t", run(String.format(rule, "=="), "{\"f\": 1000000.125, \"b\": 1000000.125}"));
            assertEquals("t", run(String.format(rule, "=="), "{\"f\": 1.5, \"b\": 1.5}"));
        }

        @Test
        void anIntegralCheckReadsTheExactValue() throws Exception {
            assertEquals("eq", run("filter { extractor { def p = parseJsonObject(span.name)\n"
                + " if (p.get(\"v\").getAsDouble() as Long == 1000000000000000128) { service \"eq\" }"
                + " else { service \"ne\" } } }", "{\"v\": 1000000000000000128}"));
            // A text that is no whole number fails the span, as LAL's `as Integer`.
            assertThrows(NumberFormatException.class, () -> run("filter { extractor { if (span.name as Integer == 5) {"
                + " service \"eq\" } else { service \"ne\" } } }", "5.0000000000000001"));
            // Two exact readings of one whole number are equal, its double's binary value included.
            assertEquals("eq", run("filter { extractor { def v = parseJsonObject(span.name).get(\"v\")\n"
                + " if (v.getAsDouble() == v.getAsBigDecimal()) { service \"eq\" } else { service \"ne\" } } }",
                "{\"v\": 1000000000000000128}"));
            // Beside a double, in double, as Groovy and LAL compare: 1e320 is an infinity there.
            assertEquals("ge", run("filter { extractor { def p = parseJsonObject(span.name)\n"
                + " if (p.get(\"v\").getAsBigDecimal() < 1.0 / 0.0) { service \"lt\" } else { service \"ge\" } } }",
                "{\"v\": 1e320}"));
        }

        @Test
        void aStringOrdersAgainstANumberAsTheNumberItReads() throws Exception {
            assertEquals("yes", run("filter { extractor { if (\"3\" < 5) { service \"yes\" } } }", "n"));
            final String status = "filter { extractor { if (span.name >= 400) { service \"yes\" } else {"
                + " service \"no\" } } }";
            assertEquals("yes", run(status, " 404 "));
            assertEquals("no", run(status, "200"));
            assertThrows(IllegalArgumentException.class, () -> run(status, "OK"));
            assertRejected("filter { extractor { if (\"abc\" < 5) { service \"x\" } } }", "'abc' is no number");
            assertThrows(IllegalArgumentException.class, () -> run("filter { extractor { if (labels().get(\"reason\")"
                + " > 400) { service \"x\" } } }", "n"));
            // Two strings: as numbers when both read as one, else as text.
            final String strings = "filter { extractor { if (span.name > \"2\") { service \"gt\" } else {"
                + " service \"le\" } } }";
            assertEquals("gt", run(strings, "10"));
            assertEquals("le", run(strings, "1"));
            assertEquals("gt", run(strings, "b"));
            // A converted value that does not convert fails the span.
            assertThrows(NumberFormatException.class, () -> run("filter { extractor { if (span.name as Integer > 400) {"
                + " service \"yes\" } else { service \"no\" } } }", "OK"));
            // A character is the one-character string it is: against a number, the number it reads as.
            final String digit = "filter { extractor { if (span.name.charAt(0) > 4) { service \"gt\" } else {"
                + " service \"le\" } } }";
            assertEquals("gt", run(digit, "5abc"));
            assertThrows(IllegalArgumentException.class, () -> run(digit, "abc"));
            // Equality reads the same way, and one that reads as no number is unequal.
            final String equal = "filter { extractor { if (span.name == 3) { service \"eq\" } else { service \"ne\" }"
                + " } }";
            assertEquals("eq", run(equal, " 3 "));
            assertEquals("ne", run(equal, "three"));
            assertEquals("eq", run("filter { extractor { if (span.name.charAt(0) == 1) { service \"eq\" } } }",
                "1x"));
            assertEquals("eq", run("filter { extractor { if (span.name.charAt(0) == \"E\") { service \"eq\" } } }",
                "ERROR"));
            // A character is written as itself, not as its code.
            assertEquals("a", run("filter { extractor { service span.name.charAt(0) } }", "abc"));
        }

        @Test
        void aNumberOfUnknownTypeEqualsTheTextItReadsAs() throws Exception {
            assertEquals("eq", run("filter { extractor { def l = emptyList()\n def a = l.add(3 as java.lang.Integer)\n"
                + " if (l.get(0) == \"3\") { service \"eq\" } else { service \"ne\" } } }", "n"));
            // A boolean against a number is unequal.
            assertEquals("ne", run("filter { extractor { def l = emptyList()\n"
                + " def a = l.add(true as java.lang.Boolean)\n if (l.get(0) == 1) { service \"eq\" } else {"
                + " service \"ne\" } } }", "n"));
        }

        @Test
        void valuesOfUnknownTypeEqualByValue() throws Exception {
            assertEquals("eq", run("filter { extractor { def l = emptyList()\n def a = l.add(span.name)\n"
                + " def b = l.add(span.name)\n if (l.get(0) == l.get(1)) { service \"eq\" } } }", "a"));
            // A value of unknown type equals a String expression it is, and a boolean.
            assertEquals("eq", run("filter { extractor { def l = emptyList()\n def a = l.add(span.name)\n"
                + " if (l.get(0) == span.name) { service \"eq\" } } }", "a"));
            assertEquals("eq", run("filter { extractor { def flag = true as java.lang.Object\n"
                + " if (flag == true) { service \"eq\" } } }", "n"));
        }

        @Test
        void aCharacterCastAsACharacterEqualsItsText() throws Exception {
            assertEquals("eq", run("filter { extractor { if (span.name.charAt(0) as java.lang.Character == \"3\") {"
                + " service \"eq\" } } }", "3x"));
        }

        @Test
        void aNullOrdersBelowEveryValue() throws Exception {
            // A cast keeps a null for `== null`, and a number orders above null.
            assertEquals("null", run("filter { extractor { if (span.name as Long == null) { service \"null\" } } }",
                ""));
            assertEquals("gt", run("filter { extractor { if (span.retries > null) { service \"gt\" } } }", "n"));
            assertEquals("lt", run("filter { extractor { if (null < span.name) { service \"lt\" } } }", "a"));
            assertEquals("lt", run("filter { extractor { if (null < span.flagged) { service \"lt\" } } }", "n"));
            assertEquals("le", run("filter { extractor { def r = null as java.util.Map\n"
                + " if (null <= r) { service \"le\" } } }", "n"));
        }

        @Test
        void twoBooleansOrder() throws Exception {
            assertEquals("gt", run("filter { extractor { def a = true as java.lang.Object\n"
                + " def b = false as java.lang.Object\n if (a > b) { service \"gt\" } } }", "n"));
        }

        @Test
        void anInlineCastToATextTypeComparesAsText() throws Exception {
            // As a def of it does.
            assertEquals("same", run("filter { extractor { if ((span.name as java.lang.CharSequence) == \"abc\") "
                + "{ service \"same\" } } }", "abc"));
        }

        @Test
        void aClassNameCastTypesAComparison() throws Exception {
            // Both casts type the comparison: two CharSequences compare as text.
            final String rule = "filter { extractor { if (thing() as java.lang.CharSequence == span.name as "
                + "java.lang.CharSequence) { service \"eq\" } else { service \"ne\" } } }";
            assertEquals("eq", run(rule, "abc"));
            assertEquals("ne", run(rule, "x"));
        }

        @Test
        void arraysCompareAndPrintByContent() throws Exception {
            // a list against an array, as in every DSL
            assertEquals("same", run("filter { extractor { def l = emptyList()\n def a = l.add(\"a\")\n"
                + " def b = l.add(\"b\")\n if (l == span.name.split(\"-\")) { service \"same\" } } }", "a-b"));
            assertEquals("[a, b]", run("filter { extractor { service span.name.split(\"-\").toString() } }", "a-b"));
            // the property spelling and a holder of a broader type print and compare as the array they hold
            assertEquals("[a, b]", run("filter { extractor { service span.name.split(\"-\").toString } }", "a-b"));
            // as String converts an array as LAL does, by its elements
            assertEquals("[a, b]", run("filter { extractor { service span.name.split(\"-\") as String } }", "a-b"));
            assertEquals("[a, b]", run("filter { extractor { def s = span.name.split(\"-\") as String\n service s } }",
                "a-b"));
            final String held = "filter { extractor { def o = span.name.split(\"-\") as java.lang.Object\n"
                + " def p = span.name.split(\"-\") as java.lang.Object\n %s } }";
            assertEquals("[a, b][a, b][a, b]", run(String.format(held,
                "service o.toString() + o?.toString() + o.toString"), "a-b"));
            assertEquals("same", run(String.format(held, "if (o == p) { service \"same\" }"), "a-b"));
            assertEquals("same", run(String.format(held, "if (span.name.split(\"-\") == o) { service \"same\" }"),
                "a-b"));
            assertEquals("same", run("filter { extractor { def m = emptyMap()\n"
                + " def p = m.put(\"x\", span.name.split(\"-\"))\n def q = m.put(\"y\", span.name.split(\"-\"))\n"
                + " if (m.get(\"x\") == m.get(\"y\")) { service \"same\" } } }", "a-b"));
        }

        @Test
        void aTextJoinsOnlyOntoATextANumberACharacterOrNull() {
            assertRejected("filter { extractor { service span.name.split(\",\") + \"-x\" } }",
                "A String[] takes no part in a text +");
            assertRejected("filter { extractor { service \"x\" + span.name.split(\",\") } }",
                "A String[] takes no part in a text +");
        }

        @Test
        void aJsonValueComparesAsWhatItHolds() throws Exception {
            final String json = "filter { extractor { def p = parseJsonObject(span.name)\n def l = emptyList()\n"
                + " def a = l.add(p.get(\"code\"))\n %s } }";
            final String doc = "{\"code\": 200, \"status\": \"ok\", \"ok\": true}";
            assertEquals("y", run(String.format(json, "if (p.get(\"code\") == 200 && p.get(\"status\") == \"ok\""
                + " && p.get(\"ok\") == true && p.getAsJsonPrimitive(\"code\") != 201) { service \"y\" }"), doc));
            assertEquals("y", run(String.format(json, "if (l.get(0) == 200) { service \"y\" }"), doc));
            assertEquals("y", run(String.format(json, "if (p.get(\"code\") == \"200\") { service \"y\" }"),
                "{\"code\": \"200\"}"));
        }

        @Test
        void aComparisonComparesValuesNotConditions() {
            assertRejected("filter { extractor { if ((span.name == \"n\") == true) { service \"t\" } } }",
                "`(span.name == \"n\")` is a condition");
            assertRejected("filter { extractor { if (true == (span.name == \"n\")) { service \"t\" } } }",
                "`(span.name == \"n\")` is a condition");
        }
    }

    @Nested
    class ArithmeticAndQuotients {
        @Test
        void plusAddsNumbersAndJoinsAnythingElse() throws Exception {
            // A number of any class adds.
            assertEquals("8", run("filter { extractor { def p = parseJsonObject(span.name)\n"
                + " service \"\" + ((p.get(\"r\").getAsNumber() as java.lang.Number) + 1) } }", "{\"r\": 7}"));
            assertEquals("2.7", run("filter { extractor { service \"\" + (parseJsonObject(span.name).get(\"r\")"
                + ".getAsBigDecimal() + 1) } }", "{\"r\": 1.7}"));
            assertEquals("6/5x", run("filter { extractor { def l = emptyList()\n"
                + " def a = l.add(5 as java.lang.Integer)\n service \"\" + (l.get(0) + 1) + \"/\" + (l.get(0) + \"x\")"
                + " } }", "n"));
            // A map's String value beside a number joins, whatever Map.get declares.
            assertEquals("31.50/", run("filter { extractor { def amount = parseJsonObject(span.name).get(\"a\")"
                + ".getAsBigDecimal()\n service amount + labels().get(\"delimiter\") } }", "{\"a\": 31.50}"));
            assertEquals("total 1.7", run("filter { extractor { service \"total \" + parseJsonObject(span.name)"
                + ".get(\"r\").getAsBigDecimal() } }", "{\"r\": 1.7}"));
            // A missing boxed number joins as "null"; arithmetic still unboxes it.
            assertEquals("retry=null", run("filter { extractor { def r = null as java.lang.Integer\n"
                + " service \"retry=\" + r } }", "n"));
            assertEquals("6", run("filter { extractor { service \"\" + (span.retries + 1) } }", "n"));
            // A float computes in double, as Groovy and LAL compute it.
            assertEquals("0.30000000447034836", run("filter { extractor { service \"\" + (0.1f + 0.2f) } }", "n"));
            // A string is never multiplied.
            assertRejected("filter { extractor { service \"\" + (\"b\" * 2) } }",
                "'*' takes numbers only, and `\"b\"` is a String");
            assertRejected("filter { extractor { service \"\" + (2 * labels().get(\"reason\")) } }",
                "is a value whose type is known only when the rule runs");
        }

        @Test
        void aBooleanJoinsOnlyAStringOnItsLeft() throws Exception {
            assertRejected("filter { extractor { service true + \"-suffix\" } }", "A boolean takes no part in '+'");
            assertRejected("filter { extractor { service \"\" + (span.retries + span.flagged) } }",
                "A boolean takes no part in '+'");
            assertEquals("x-false", run("filter { extractor { service \"x-\" + span.flagged } }", "n"));
            // known only when the rule runs: refused then
            assertThrows(Exception.class, () -> run("filter { extractor { def m = emptyMap()\n"
                + " def p = m.put(\"b\", true)\n service m.get(\"b\") + \"-suffix\" } }", "n"));
        }

        @Test
        void twoCharactersJoinAsText() throws Exception {
            assertEquals("ca", run("filter { extractor { service \"\" + (span.name.charAt(1) + span.name.charAt(2))"
                + " } }", "/cart"));
        }

        @Test
        void aGroupedJoinIsOneOperand() throws Exception {
            assertEquals("55/cart", run("filter { extractor { service meta.latency + (meta.latency + span.name) } }",
                "/cart"));
            assertEquals("55/cart", run("filter { extractor { service meta.latency + ((meta.latency + span.name))"
                + " } }", "/cart"));
        }

        @Test
        void aMethodResultTakesPartInArithmeticByItsType() throws Exception {
            // A method's numeric result adds.
            assertEquals("6", run("filter { extractor { service \"\" + (meta.latency.longValue() + 1) } }", "n"));
            assertEquals("6.0", run("filter { extractor { service \"\" + (meta.latency.doubleValue() + 1) } }",
                "n"));
            // Beside a String, a number of any class joins as text.
            assertEquals("{\"r\": 1.7}1.7", run("filter { extractor { service span.name"
                + " + parseJsonObject(span.name).get(\"r\").getAsBigDecimal() } }", "{\"r\": 1.7}"));
        }

        @Test
        void aMethodResultKeepsItsOwnTypeInADef() throws Exception {
            assertEquals("31", run("filter { extractor { def s = round(2.5).toString()\n service s + 1 } }", "n"));
            assertEquals("31", run("filter { extractor { service round(2.5).toString() + 1 } }", "n"));
        }

        @Test
        void aPropertyTakesPartInArithmeticByItsType() throws Exception {
            assertEquals("4", run("filter { extractor { service \"\" + (span.name.length + 1) } }", "abc"));
            // A meta field read as a method is typed as the method the code calls, as a span field is.
            assertEquals("3", run("filter { extractor { service \"\" + (meta.endpoint.length - 1) } }", "n"));
            assertEquals("3", run("filter { extractor { service \"\" + (span.name.length - 1) } }", "abcd"));
        }

        @Test
        void aGuardedPrimitiveFailsArithmeticByItsConversion() {
            // span.child is null: its `?.` reads null, which the conversion refuses.
            for (final String sum : List.of("span.child?.weight + 1", "(span.child?.weight) + 1")) {
                final Exception e = assertThrows(IllegalArgumentException.class,
                    () -> run("filter { extractor { service \"\" + (" + sum + ") } }", "n"));
                assertTrue(e.getMessage().contains("null cannot be converted to double"), e.getMessage());
            }
        }

        @Test
        void aGuardedNumberJoinsAsNull() throws Exception {
            assertEquals("nullms", run("filter { extractor { def p = parseJsonObject(span.name)\n"
                + " service p.get(\"optional\")?.getAsInt() + \"ms\" } }", "{}"));
        }

        @Test
        void aJsonNumberAddsByItsExactValue() throws Exception {
            assertEquals("2.75", run("filter { extractor { def p = parseJsonObject(span.name)\n"
                + " service \"\" + (p.get(\"amount\").getAsNumber() + 1) } }", "{\"amount\": 1.75}"));
            assertEquals("4294967297", run("filter { extractor { def p = parseJsonObject(span.name)\n"
                + " service \"\" + (p.get(\"amount\").getAsNumber() + 1) } }", "{\"amount\": 4294967296}"));
        }

        @Test
        void aBigIntegerBesideAWholeNumberStaysWhole() throws Exception {
            // So it divides exactly.
            assertEquals("7.002", run("filter { extractor { def p = parseJsonObject(span.name)\n"
                + " service \"\" + ((p.get(\"ns\").getAsBigInteger() + 1) / 1000) } }", "{\"ns\": 7001}"));
        }

        @Test
        void aNumberOfAClassKnownOnlyWhenTheRuleRunsComputesAsGroovys() throws Exception {
            // An int wraps.
            assertEquals("-2147483648", run("filter { extractor { def n = 2147483647 as java.lang.Number\n"
                + " service \"\" + (n + 1) } }", "n"));
            assertRejected("filter { extractor { def n = 0.5 as java.lang.Number\n service \"\" + (n / 3) } }",
                "'/' of a number whose class is known only when the rule runs");
        }

        @Test
        void aClassNameCastTypesArithmetic() throws Exception {
            // A Short adds as a number.
            assertEquals("8", run("filter { extractor { service \"\" + ((boxedSmall() as java.lang.Short) + 1) } }",
                "n"));
        }

        @Test
        void divisionGivesTheExactDecimalUntilATargetConvertsIt() throws Exception {
            // A quotient used as a value is the decimal; written to an int or long target, the whole-number division.
            assertEquals("3.5", run("filter { extractor { service \"\" + (7 / 2) } }", "n"));
            assertEquals("yes", run("filter { extractor { if (7 / 2 > 3) { service \"yes\" } } }", "n"));
            assertEquals("3", run("filter { extractor { def n = 7 / 2 as Long\n service \"\" + n } }", "n"));
            // A method's argument takes the decimal, so it converts first, as in LAL.
            assertRejected("filter { extractor { service span.name.substring(5 / 2) } }",
                "String.substring(BigDecimal) matches no method");
            assertEquals("cd", run("filter { extractor { service span.name.substring((5 / 2) as Integer) } }",
                "abcd"));
            assertThrows(ArithmeticException.class, () -> run("filter { extractor { service \"\" + (7 / 0) } }", "n"));
        }

        @Test
        void aQuotientComparedAsLongStaysExact() throws Exception {
            assertEquals("eq", run("filter { extractor { def ns = 1699999999999999999L\n"
                + " if (ns / 1000000 as Long == 1699999999999L) { service \"eq\" } else { service \"ne\" } } }", "n"));
        }

        @Test
        void onlyTheDivisionsReachingTheTargetAreWhole() throws Exception {
            // A whole-number quotient divided again by zero fails.
            assertThrows(ArithmeticException.class, () -> run("filter { extractor { def n = 0\n"
                + " service \"\" + (meta.latency / 2 / n) } }", "n"));
            assertEquals("7", run("filter { extractor { def x = 7 / 2 * 2 as Long\n service \"\" + x } }", "n"));
            assertEquals("7", run("filter { extractor { def x = (7 / 2) * 2 as Long\n service \"\" + x } }", "n"));
            assertEquals("3", run("filter { extractor { def x = 7 / 2 / 1 as Long\n service \"\" + x } }", "n"));
        }

        @Test
        void aQuotientIsTheExactDecimalUntilItsDestinationConvertsIt() throws Exception {
            // Through parentheses and a reassignment, the timestamp stays exact.
            assertEquals("1699999999999", run("filter { extractor { def ns = 1699999999999999999L\n"
                + " def ms = (ns / 1000) / 1000 as Long\n service \"\" + ms } }", "n"));
            assertEquals("1699999999999", run("filter { extractor { def ns = 1699999999999999999L\n def ms = 0L\n"
                + " ms = (ns / 1000) / 1000\n service \"\" + ms } }", "n"));
            // A zero divisor fails, a quotient's held in a def or grouped included.
            assertThrows(ArithmeticException.class, () -> run("filter { extractor { def n = 0\n"
                + " def q = meta.latency / 2\n service \"\" + (q / n) } }", "n"));
            assertThrows(ArithmeticException.class, () -> run("filter { extractor { def n = 0\n"
                + " service \"\" + (meta.latency / (n / 2)) } }", "n"));
            // A cast of a method's result leaves its argument the decimal.
            assertRejected("filter { extractor { def n = span.name.substring(5 / 2) as Long\n service \"x\" } }",
                "String.substring(BigDecimal) matches no method");
            // Non-terminating: ten decimals, half up; as text its digits, compared exactly.
            assertEquals("2.3333333333", run("filter { extractor { service \"\" + (7 / 3) } }", "n"));
            assertEquals("yes", run("filter { extractor { if (1 / 3 * 3 < 1) { service \"yes\" } } }", "n"));
        }

        @Test
        void aQuotientRoundsAsGroovy() throws Exception {
            // Ten digits beyond the precision, then the larger scale or ten decimals, half up.
            assertEquals("16", run("filter { extractor { def x = 16 / 11 * 11 as Long\n service \"\" + x } }", "n"));
            assertEquals("1.4545454546", run("filter { extractor { service \"\" + (16 / 11) } }", "n"));
            assertEquals("0.99999999998896210282", run("filter { extractor {"
                + " service \"\" + (90596966399 / 1048576 / 86400) } }", "n"));
        }

        @Test
        void aQuotientBesideADoubleComputesInDouble() throws Exception {
            // A zero divisor is an infinity there.
            assertEquals("0.42857142857142855", run("filter { extractor { service \"\" + (6 / 2 / 7.0) } }", "n"));
            assertEquals("Infinity", run("filter { extractor { service \"\" + (6 / 2 / 0.0) } }", "n"));
            // It stays a double through grouping, and fails beside a null.
            assertEquals("7.0", run("filter { extractor { service \"\" + ((6 / 2 + 0.5) * 2) } }", "n"));
            assertThrows(IllegalArgumentException.class, () -> run("filter { extractor { def n = null as Long\n"
                + " service \"\" + ((6 / 2 + 0.5) + n) } }", "n"));
        }

        @Test
        void arithmeticOnAQuotientKeepsItADecimal() throws Exception {
            // It multiplies, checks its overloads, fails on a null.
            assertEquals("8", run("filter { extractor { service \"\" + ((6 / 2 + 1) * 2) } }", "n"));
            assertRejected("filter { extractor { service span.name.substring(5 / 2 + 0) } }",
                "String.substring(BigDecimal) matches no method");
            assertThrows(NullPointerException.class, () -> run("filter { extractor { def n = null as Long\n"
                + " service \"\" + (6 / 2 + n) } }", "n"));
        }
    }

    @Nested
    class DefFlowFacts {
        @Test
        void onlyAQuotientNarrowsIntoADefHoldingAWholeNumber() throws Exception {
            assertEquals("1699999999999", run("filter { extractor { def ms = 0L\n"
                + " ms = 1699999999999999999L / 1000000L\n service \"\" + ms } }", "n"));
            assertRejected("filter { extractor { def n = 0\n def p = parseJsonObject(span.name)\n"
                + " n = p.get(\"items\").getAsNumber()\n service \"x\" } }", "def n holds a Integer");
            assertEquals("3", run("filter { extractor { def n = 0\n n = (7 / 2) + 0\n service \"\" + n } }", "n"));
            // A stored quotient narrows too.
            assertEquals("7", run("filter { extractor { def n = 0L\n def q = 7001 / 1000\n n = q\n"
                + " service \"\" + n } }", "n"));
        }

        @Test
        void aDefHoldsAQuotientAfterABranchOnlyWhenEveryPathGivesOne() throws Exception {
            final String json = "filter { extractor { def p = parseJsonObject(span.name)\n"
                + " def q = p.get(\"amount\").getAsNumber()\n %s\n def n = 0L\n n = q\n service \"\" + n } }";
            assertRejected(String.format(json, "if (meta.latency > 100) { q = 7 / 2 }"),
                "def n holds a Long and is reassigned a Number");
            assertEquals("4", run(String.format(json, "if (meta.latency > 100) { q = 7 / 2 } else { q = 9 / 2 }"),
                "{\"amount\": 8.75}"));
        }

        @Test
        void aStoredQuotientStaysOneThroughGroupingAndArithmetic() throws Exception {
            final String stored = "filter { extractor { def q = meta.latency / 2\n def n = 0L\n n = %s\n"
                + " service \"\" + n } }";
            assertEquals("2", run(String.format(stored, "(q)"), "n"));
            assertEquals("3", run(String.format(stored, "q + 1"), "n"));
        }

        @Test
        void aDefHoldsTheRulesOwnListAfterABranchOnlyWhenEveryPathThatGoesOnGivesOne() throws Exception {
            assertRejected("filter { extractor { def l = emptyList()\n if (meta.latency > 1) { l = span.children }\n"
                + " def a = l.add(\"v\")\n service \"x\" } }", "add() changes only a map or list the rule made");
            assertRejected("filter { extractor { def l = emptyList()\n if (meta.latency > 1) { l = span.children }"
                + " else { l = emptyList() }\n def a = l.add(\"v\")\n service \"x\" } }", "add() changes only");
            assertEquals("1", run("filter { extractor { def l = emptyList()\n if (meta.latency > 1) {"
                + " l = emptyList() } else { abort {} }\n def a = l.add(\"v\")\n service \"\" + l.size() } }", "n"));
        }

        @Test
        void aDefABranchDidNotAssignIsNullThereWhichAGuardExcludes() throws Exception {
            assertEquals("1", run("filter { extractor { if (meta.latency == 5) { def m = emptyMap() }\n"
                + " if (m != null) { def old = m.put(\"a\", 1) }\n service \"\" + m.size() } }", "n"));
            assertEquals("2", run("filter { extractor { if (meta.latency == 5) { def q = meta.latency / 2 }\n"
                + " def n = 0L\n if (q != null) { n = q }\n service \"\" + n } }", "n"));
        }

        @Test
        void groupingAnotherDefAClassCastAndAHeldReceiverKeepTheRulesOwnMap() throws Exception {
            assertEquals("5", run("filter { extractor { def m = (emptyMap())\n def a = m.put(\"x\", 1)\n"
                + " def tags = m\n def b = tags.put(\"y\", 2)\n def c = (m).put(\"z\", 3)\n"
                + " def d = (m as java.util.Map).put(\"w\", 4)\n def e = (tags)?.put(\"v\", 5)\n"
                + " service \"\" + m.size() } }", "n"));
            assertRejected("filter { extractor { def m = labels()\n def c = (m).put(\"z\", 3)\n service \"x\" } }",
                "put() changes only a map or list the rule made");
        }

        @Test
        void aLoopNeedsTheRulesOwnMapOnlyWhereItWritesIt() throws Exception {
            // a fresh emptyMap() on both paths, then put: what the iteration started with is not written
            assertNotNull(compile("filter { extractor { def m = emptyMap()\n foreach (x in span.names) {"
                + " if (x == \"a\") { m = emptyMap() } else { m = emptyMap() }\n def p = m.put(\"a\", x)\n"
                + " m = labels() } } }"));
            // a copy that is never written needs nothing
            assertNotNull(compile("filter { extractor { def m = emptyMap()\n def n = emptyMap()\n"
                + " foreach (x in span.names) { n = m\n m = labels() } } }"));
            // a write through a copy needs what it copied
            assertThrows(Exception.class, () -> compile("filter { extractor { def m = emptyMap()\n def n = emptyMap()\n"
                + " foreach (x in span.names) { n = m\n def p = n.put(\"a\", x)\n m = labels() } } }"));
        }

        @Test
        void whatALoopHoldsSettlesHoweverLongTheChainOfCopies() throws Exception {
            // each iteration moves what a def holds one copy further: as many generations as copies
            final StringBuilder defs = new StringBuilder();
            final StringBuilder copies = new StringBuilder();
            for (int i = 0; i < 80; i++) {
                defs.append("def m").append(i).append(" = emptyMap()\n");
                copies.append("m").append(i).append(" = m").append(i + 1).append('\n');
            }
            assertNotNull(compile("filter { extractor { " + defs + "def m80 = emptyMap()\n foreach (x in span.names) { "
                + copies + "m80 = labels() } } }"));
            assertThrows(Exception.class, () -> compile("filter { extractor { " + defs + "def m80 = emptyMap()\n"
                + " foreach (x in span.names) { def p = m0.put(\"a\", x)\n " + copies + "m80 = labels() } } }"),
                "a write through the end of the chain still needs the rule's own map");
        }

        @Test
        void aLoopWritesAMapItReCreatesEachTime() throws Exception {
            // every put follows a fresh emptyMap() in the same iteration, whatever the iteration started with
            assertNotNull(compile("filter { extractor { def m = emptyMap()\n foreach (x in span.names) {"
                + " m = emptyMap()\n def p = m.put(\"a\", x)\n m = labels() } } }"));
        }

        @Test
        void aLoopsNextIterationSeesWhatTheBodyAssigned() throws Exception {
            assertRejected("filter { extractor { def m = emptyMap()\n foreach (c in span.children) {"
                + " def x = m.put(c.label, \"v\")\n m = labels() }\n service \"x\" } }",
                "put() changes only a map or list the rule made");
            assertNotNull(compile("filter { extractor { def m = emptyMap()\n foreach (c in span.children) {"
                + " def x = m.put(c.label, \"v\") }\n service \"\" + m.size() } }"));
        }

        @Test
        void aLoopKeepsAMapTheRuleOwnsAndAQuotientEachIterationAssignsAgain() throws Exception {
            assertEquals("1", run("filter { extractor { def m = emptyMap()\n foreach (c in pair(\"end\", \"b\")) {"
                + " def x = m.put(c, \"seen\")\n if (c == \"end\") { m = emptyMap() } }\n service \"\" + m.size()"
                + " } }", "n"));
            assertEquals("1", run("filter { extractor { def last = meta.latency / 2\n def n = 0L\n"
                + " foreach (c in pair(\"a\", \"b\")) { n = last\n last = meta.latency / 5 }\n service \"\" + n } }",
                "n"));
        }

        @Test
        void whatAnyIterationCanStartWithHoldsOnEveryIteration() throws Exception {
            // out reads q, which reads future a step later: the third iteration's q is the JSON decimal
            final String chain = "filter { extractor { def p = parseJsonObject(span.name)\n"
                + " def q = meta.latency / 2\n def future = meta.latency / 5\n def out = 0L\n"
                + " foreach (c in pair(\"a\", \"b\")) { out = %s\n q = future\n future = %s }\n"
                + " service \"\" + out } }";
            assertRejected(String.format(chain, "q", "p.get(\"amount\").getAsBigDecimal()"),
                "def out holds a Long and is reassigned a BigDecimal");
            assertRejected(String.format(chain, "q + 0", "p.get(\"amount\").getAsBigDecimal()"),
                "def out holds a Long and is reassigned a BigDecimal");
            // latency 5: the second iteration's out is the first iteration's future, 5 / 5
            assertEquals("1", run(String.format(chain, "q", "meta.latency / 3"), "{\"amount\": 9.75}"));
        }
    }

    @Nested
    class MethodCalls {
        @Test
        void aRuleCallsTheSupportedMethodsAlone() throws Exception {
            assertRejected("filter { extractor { service span.name.strip() } }",
                "String.strip(...) is not supported in a TAL rule. A String allows [charAt, concat, contains");
            assertRejected("filter { extractor { service span.name.format(\"%s\", span.name) } }",
                "String.format(...) is a static method");
            assertRejected("filter { extractor { service span.name.getClass().getName() } }",
                "String.getClass(...) is not supported in a TAL rule");
            assertRejected("filter { extractor { service \"\" + (thing()).hashCode() } }",
                "Object.hashCode(...) is not supported in a TAL rule. An Object allows [equals, toString]");
            assertRejected("filter { extractor { def c = span.child\n service c.value(\"x\") } }",
                "TestSpanChild.value(...) is not supported in a TAL rule");
            // put and add change only the rule's own map or list.
            assertRejected("filter { extractor { def m = labels()\n def x = m.put(\"k\", \"v\")\n service \"x\" } }",
                "put() changes only a map or list the rule made with emptyMap()");
            assertEquals("v", run("filter { extractor { def m = emptyMap()\n def x = m.put(\"k\", \"v\")\n"
                + " service m.get(\"k\") } }", "n"));
            assertEquals("x", run("filter { extractor { service span.getName() } }", "x"));
        }

        @Test
        void aMethodNoValueHasNamesTheSupportedOnes() {
            assertRejected("filter { extractor { service span.name.normalise() } }",
                "String.normalise(...) is not supported in a TAL rule. A String allows [charAt, concat");
            assertRejected("filter { extractor { def s = span.name\n service s.normalise() } }",
                "String.normalise(...) is not supported in a TAL rule");
        }

        @Test
        void aPropertyReadCallsASupportedMethodAlone() throws Exception {
            assertRejected("filter { extractor { service span.name.strip } }",
                "String.strip(...) is not supported in a TAL rule");
            assertRejected("filter { extractor { service (span.name as java.lang.String).strip } }",
                "String.strip(...) is not supported in a TAL rule");
            assertRejected("filter { extractor { def s = span.name\n service s.strip } }",
                "String.strip(...) is not supported in a TAL rule");
            assertRejected("filter { extractor { service \"\" + meta.service.bytes } }",
                "String.getBytes(...) is not supported in a TAL rule");
            assertEquals("3", run("filter { extractor { service \"\" + span.name.length } }", "abc"));
        }

        @Test
        void hashCodeIsNoGetter() {
            assertRejected("filter { extractor { service \"\" + span.hashCode() } }",
                "TestNestedSpanInput.hashCode(...) is not supported in a TAL rule");
            assertRejected("filter { extractor { service \"\" + span.hashCode } }",
                "TestNestedSpanInput.hashCode(...) is not supported in a TAL rule");
        }

        @Test
        void anUnknownPropertyNamesWhatTheValueAllows() {
            assertRejected("filter { extractor { def n = span.name\n service n.typo } }",
                "String.typo reads no property");
            assertRejected("filter { extractor { service span.name.typo } }", "typo");
        }

        @Test
        void aMapAnswersObjectsMethodsAndASetTheCollectionMethods() throws Exception {
            assertEquals("[]", run("filter { extractor { def m = emptyMap()\n service m.keySet().toString() } }",
                "n"), "a Map answers Object's methods");
            final String element = "filter { extractor { def p = parseJsonObject(span.name)\n service \"\" + %s } }";
            assertEquals("true", run(String.format(element, "p.keySet().contains(\"ns\")"), "{\"ns\": 7001}"));
            assertEquals("true", run(String.format(element, "p.isJsonObject()"), "{\"ns\": 7001}"));
        }

        @Test
        void theJsonAccessorsAreCallable() throws Exception {
            final String element = "filter { extractor { def p = parseJsonObject(span.name)\n service \"\" + %s } }";
            final String doc = "{\"id\": 42, \"port\": 80, \"c\": \"x\", \"none\": null}";
            assertEquals("42", run(String.format(element, "p.get(\"id\").getAsJsonPrimitive().getAsString()"), doc));
            assertEquals("42", run(String.format(element, "p.getAsJsonPrimitive(\"id\").getAsInt()"), doc));
            assertEquals("80", run(String.format(element, "p.get(\"port\").getAsShort()"), doc));
            assertEquals("80", run(String.format(element, "p.get(\"port\").getAsByte()"), doc));
            assertEquals("x", run(String.format(element, "p.get(\"c\").getAsCharacter()"), doc));
            assertEquals("true", run(String.format(element, "p.get(\"none\").getAsJsonNull().isJsonNull()"), doc));
            // through a primitive too: a primitive result takes its box's methods
            assertEquals("42", run(String.format(element, "p.get(\"id\").getAsInt().toString()"), doc));
            assertEquals("3", run("filter { extractor { service round(2.5).toString() } }", "n"));
        }

        @Test
        void aMethodAfterAPrimitiveResolvesOnItsBox() throws Exception {
            // A primitive result is boxed for the logger's arguments.
            final List<LogEvent> events = capture();
            run("filter { log info \"{} {}\", meta.latency.doubleValue(), (span.name.length()).longValue()\n"
                + " extractor { service \"x\" } }", "svc");
            assertEquals("5.0 3", events.get(0).getMessage().getFormattedMessage());
            assertEquals("6", run("filter { extractor { service \"\" + ((span.name.length()).longValue() + 3) } }",
                "svc"));
            // Read as a property, after a meta field and a span field alike.
            assertEquals("5.0", run("filter { extractor { service \"\" + meta.latency.doubleValue } }", "n"));
            assertEquals("0.0", run("filter { extractor { service \"\" + span.startTime.doubleValue } }", "n"));
        }

        @Test
        void metaReadsAPropertyAndAGuardedOne() throws Exception {
            assertEquals("4", run("filter { extractor { service \"\" + meta.endpoint.length } }", "n"));
            assertEquals("true", run("filter { extractor { service \"\" + meta.service?.empty } }", "n"));
        }

        @Test
        void aMethodIsChosenByHowItsArgumentsFit() throws Exception {
            // No overload takes a char, so the one-argument reading by a String parameter finds the character as
            // text.
            assertEquals("3", run("filter { extractor { service \"\" + span.name.indexOf(meta.endpoint.charAt(0))"
                + " } }", "abc/def"));
            assertEquals("3", run("filter { extractor { service \"\" + span.name.indexOf(meta.endpoint.charAt(0)"
                + " as java.lang.Character) } }", "abc/def"));
            // A value of unknown type that two overloads take is converted first, as in LAL.
            assertRejected("filter { extractor { service \"\" + span.name.indexOf(thing()) } }",
                "matches more than one method");
            assertEquals("1", run("filter { extractor { service \"\" + span.name.indexOf(thing() as String) } }",
                "xabc"));
            // No string is read as a number, nor a number as a string.
            assertRejected("filter { extractor { service span.name.concat(42) } }",
                "String.concat(int) matches no method");
            assertRejected("filter { extractor { service span.name.substring(span.name) } }",
                "String.substring(String) matches no method");
            // remove and a static method through a value are no methods a rule calls.
            assertRejected("filter { extractor { def q = emptyList()\n def i = 0\n service \"\" + q.remove(i) } }",
                "List.remove(...) is not supported in a TAL rule");
            assertRejected("filter { extractor { def amount = parseJsonObject(span.name).get(\"a\")"
                + ".getAsBigDecimal()\n def n = meta.latency as Long\n service amount.valueOf(n).toPlainString() } }",
                "BigDecimal.valueOf(...) is a static method");
        }

        @Test
        void aMapOrListValueTwoOverloadsTakeIsConvertedFirst() throws Exception {
            // indexOf(String) and indexOf(int) both take a value of unknown type.
            assertRejected("filter { extractor { service \"\" + span.name.indexOf(labels().get(\"delimiter\")) } }",
                "String.indexOf(a value of unknown type) matches more than one method");
            assertRejected("filter { extractor { service \"\" + span.name.indexOf(labels().get(\"delimiter\"), 1)"
                + " } }", "matches more than one method");
            assertEquals("3", run("filter { extractor { service \"\" + span.name.indexOf(labels().get(\"delimiter\")"
                + " as String) } }", "abc/def"));
            // One overload that takes it keeps working.
            assertEquals("3", run("filter { extractor { def sep = 47\n service \"\" + span.name.indexOf(sep) } }",
                "abc/def"));
            assertRejected("filter { extractor { def text = \"\"\n service text.valueOf(span.name.length()) } }",
                "String.valueOf(...) is a static method");
        }

        @Test
        void aPrimitiveBooleanArgumentConvertsAsAsBooleanAndFailsOnNull() throws Exception {
            final String flag = "filter { extractor { service flag(labels().get(\"%s\")) } }";
            assertNotNull(compile(String.format(flag, "absent")));
            final Exception missing = assertThrows(IllegalArgumentException.class,
                () -> run(String.format(flag, "absent"), "abc"));
            assertTrue(missing.getMessage().contains("null cannot be converted to boolean"), missing.getMessage());
            // a text converts as as Boolean does: true or false in any case, nothing else
            final String named = "filter { extractor { service flag(span.name) } }";
            assertEquals("on", run(named, "TRUE"));
            assertEquals("off", run(named, "false"));
            final Exception timeout = assertThrows(IllegalArgumentException.class,
                () -> run(String.format(flag, "reason"), "abc"));
            assertTrue(timeout.getMessage().contains("'timeout' cannot be converted to boolean"), timeout.getMessage());
        }

        @Test
        void aCharacterPassesToACharParameter() throws Exception {
            assertEquals("bbc", run("filter { extractor { service span.name.replace(span.name.charAt(0),"
                + " span.name.charAt(1)) } }", "abc"));
        }

        @Test
        void aBoxedPrimitiveReceiverIsEvaluatedOnce() throws Exception {
            // `?.` checks the held value, not a second call.
            assertEquals("true/1", run("filter { extractor { def l = emptyList()\n"
                + " service (l.add(span.name))?.toString() + \"/\" + l.size() } }", "x"));
            assertEquals("true1", run("filter { extractor { def l = emptyList()\n"
                + " service (l?.add(span.name))?.toString() + l.size() } }", "x"));
        }

        @Test
        void aGuardedReceiverKeepsItsSafeNavigation() throws Exception {
            assertEquals("v=null", run("filter { extractor { def p = parseJsonObject(span.name)\n"
                + " service \"v=\" + (p.get(\"optional\")?.getAsInt())?.toString() } }", "{}"));
        }

        @Test
        void aGuardedArrayElementIsBoxedOnce() throws Exception {
            assertNotNull(compile("filter { extractor { def b = span.bytes\n service \"\" + b[0].intValue() } }"));
        }

        @Test
        void aGroupedSafeNavigationKeepsTheListsElementType() throws Exception {
            // for the loop
            assertNotNull(compile("filter { extractor { def kids = (span)?.getChildren()\n"
                + " foreach (c in kids) { service c.label } } }"));
        }

        @Test
        void aHeldValueIsDeclaredOnce() throws Exception {
            // so a short rule stays small
            assertNotNull(compile("filter { extractor { def p = parseJsonObject(span.name)\n"
                + " service \"\" + (((((p.get(\"latency\"))?.getAsLong() / 1000 + 5) * 60 / 45 + 1) * 1000 - 50)"
                + " / 60) } }"));
        }
    }

    @Nested
    class Literals {
        @Test
        void aLiteralTakesNoMember() throws Exception {
            assertRejected("filter { extractor { def svc = \"ABC\"\n service \"svc\".toLowerCase() } }",
                "a literal takes no method, field or index, as '\"svc\".toLowerCase()' at line 2");
            assertRejected("filter { extractor { service \"\" + (\"abc\".length() + 1) } }",
                "a literal takes no method");
            assertRejected("filter { extractor { service \"x\" + \"a,b\".split(\",\")[0] } }",
                "a literal takes no method");
            assertRejected("filter { extractor { service 42.toString() } }", "a literal takes no method");
            // Grouped, or held in a def, it is the receiver.
            assertEquals("svc", run("filter { extractor { def svc = \"ABC\"\n service (\"SVC\").toLowerCase() } }",
                "n"));
        }

        @Test
        void anIntegerWithALeadingZeroIsOctalWhereverItStands() throws Exception {
            assertEquals("8", run("filter { extractor { service \"\" + 010 } }", "n"));
            assertEquals("9", run("filter { extractor { def n = 010L\n service \"\" + (n + 1) } }", "n"));
            // In a comparison too.
            assertEquals("eq", run("filter { extractor { if (meta.latency == 05) { service \"eq\" }"
                + " else { service \"ne\" } } }", "n"));
            assertEquals("eq", run("filter { extractor { if (010 == 8) { service \"eq\" } else { service \"ne\" }"
                + " } }", "n"));
            assertEquals("bc", run("filter { extractor { service span.name.substring(01) } }", "abc"));
            assertEquals("4294967295", run("filter { extractor { service \"\" + 037777777777 } }", "n"));
            assertNotNull(compile("filter { persist { sampler { rateLimit(\"r-${010}\") { rpm 1 } } } }"));
            // 08 is no octal number.
            assertRejected("filter { extractor { service \"\" + 08 } }",
                "the number 08 at line 1 is no octal number: an integer with a leading 0 is octal, as in Groovy");
            assertRejected("filter { extractor { if (meta.latency > 0129) { service \"x\" } } }", "the number 0129");
            // So it is in the configuration numbers.
            assertEquals("b", run("filter { extractor { service span.name.split(\",\")[01] } }", "a,b,c"));
            assertNotNull(compile("filter { persist { sampler { rateLimit(\"r\") { rpm 010 } } } }"));
            assertRejected("filter { persist { sampler { rateLimit(\"r\") { rpm 08 } } } }",
                "rpm value '08' is no octal number");
            assertRejected("filter { extractor { service span.name.split(\",\")[09] } }",
                "[index] value '09' is no octal");
            // 010 is 8, below 9.
            assertNotNull(compile("filter { extractor { def svc = service(\"s\", \"GENERAL\")\n metrics { name \"h\";"
                + " scope svc; function percentile; value 1; buckets [010, 9]; ranks [010, 9] } } }"));
            assertRejected("filter { extractor { def svc = service(\"s\", \"GENERAL\")\n metrics { name \"h\";"
                + " scope svc; function percentile; value 1; buckets [08, 20] } } }", "bucket 08 is no octal number");
            // A fraction, an exponent or a float suffix makes it decimal, as Java reads it.
            assertEquals("0.5/1000000.0/10.0/10.0/0/0", run("filter { extractor {"
                + " service \"\" + 00.5 + \"/\" + 010e5 + \"/\" + 010F + \"/\" + 010D + \"/\" + 0 + \"/\" + 0L } }",
                "n"));
        }

        @Test
        void aWholeNumberMustFitALong() {
            assertRejected("filter { extractor { service \"\" + 9223372036854775808L } }", "is beyond a long");
        }

        @Test
        void aStringEscapesOnlyAQuoteAndABackslash() throws Exception {
            // \\t, \\s, octal, unicode and \\d stay as written
            assertEquals("a\\tb\\sc|\\101\\u0041|\\d|q\"|b\\", run("filter { extractor { service "
                + "\"a\\tb\\sc|\\101\\u0041|\\d|q\\\"|b\\\\\" } }", "n"));
            assertEquals("C:\\data", run("filter { extractor { service 'C:\\data' } }", "n"));
        }
    }

    @Nested
    class EntitiesAndLayers {
        @Test
        void anEntityIsUsedAsItsConstructorBuildsIt() {
            assertRejected("filter { extractor { metrics { name \"c\"; scope service(\"s\", \"GENERAL\").foo; "
                + "function cpm; value 1 } } }", "nothing may follow the constructor");
            assertRejected("filter { extractor { def svc = service(\"s\", \"GENERAL\").foo\n"
                + " metrics { name \"c\"; scope svc; function cpm; value 1 } } }", "nothing may follow the constructor");
            assertRejected("filter { extractor { def svc = service(\"s\", \"GENERAL\") as String\n"
                + " metrics { name \"c\"; scope svc; function cpm; value 1 } } }", "as String");
            assertRejected("filter { extractor { def svc = service(\"s\", \"GENERAL\")\n"
                + " metrics { name \"c\"; scope svc.whatever; function cpm; value 1 } } }",
                "scope takes an entity constructor or the name of a def holding one, not `svc.whatever`");
            // A string literal is no def's name.
            assertRejected("filter { extractor { def svc = service(\"s\", \"GENERAL\")\n"
                + " metrics { name \"c\"; scope \"svc\"; function cpm; value 1 } } }", "not `\"svc\"`");
        }

        @Test
        void aRelationsDetectPointIsTheKeywordAlone() {
            assertRejected("filter { extractor { metrics { name \"r\"; scope serviceRelation((\"CLIENT\")"
                + ".toLowerCase(), \"a\", \"GENERAL\", \"b\", \"GENERAL\", 0); function cpm; value 1 } } }",
                "first argument is CLIENT or SERVER");
        }

        @Test
        void aLiteralLayerIsCheckedWhenTheRuleCompiles() throws Exception {
            assertRejected("filter { extractor { metrics { name \"c\"; scope service(\"s\", \"NO_SUCH_LAYER\"); "
                + "function cpm; value 1 } } }", "'NO_SUCH_LAYER' names no registered layer");
            assertRejected("filter { extractor { metrics { name \"r\"; scope serviceRelation(CLIENT, \"a\","
                + " \"GENERAL\", \"b\", \"NOPE\", 0); function cpm; value 1 } } }", "'NOPE' names no registered layer");
            assertRejected("filter { extractor { layer \"OTLP_GENERL\" } }", "'OTLP_GENERL' names no registered layer");
            // In parentheses, converted as String, or joined from literals, a name is still spelled out in the rule.
            assertRejected("filter { extractor { layer(\"OTLP_GENERL\") } }",
                "'OTLP_GENERL' names no registered layer");
            assertRejected("filter { extractor { metrics { name \"c\"; scope service(meta.service,"
                + " (\"OTLP_GENERL\")); function cpm; value 1 } } }", "'OTLP_GENERL' names no registered layer");
            assertRejected("filter { extractor { layer (\"OTLP_GENERL\" as String) } }", "'OTLP_GENERL' names no");
            assertRejected("filter { extractor { layer (\"OTLP_GENERL\" as java.lang.String) } }",
                "'OTLP_GENERL' names no");
            assertRejected("filter { extractor { layer \"OTLP_\" + \"GENERL\" } }", "'OTLP_GENERL' names no");
            assertRejected("filter { extractor { def l = (\"OTLP_GENERL\") as Layer\n service \"x\" } }",
                "'OTLP_GENERL' as Layer names no registered layer");
            assertNotNull(compile(
                "filter { extractor { layer \"OTLP_GENERAL\"\n metrics { name \"c\"; " + SCOPE + " } } }"));

            // A layer the rule's own file declares is registered before its rules compile.
            final TALConfigs file = TALConfigs.parse("strictness-layers",
                ("layerDefinitions:\n  - name: TAL_STRICTNESS_LITERAL_LAYER\n    ordinal: 9941\n    normal: true\n"
                    + "rules:\n  - name: r\n    layer: TAL_STRICTNESS_LITERAL_LAYER\n    dsl: |\n"
                    + "      filter { extractor { metrics { name \"declared\"; "
                    + "scope service(\"s\", \"TAL_STRICTNESS_LITERAL_LAYER\"); function cpm; value 1 } } }\n")
                    .getBytes(StandardCharsets.UTF_8), "strictness-layers.yaml");
            assertNotNull(compile(file.getRules().get(0).getDsl()));
        }

        @Test
        void aLayerSpelledInLiteralsOfAnyKindIsChecked() throws Exception {
            assertRejected("filter { extractor { metrics { name \"c\"; scope service(\"payments\", 1000); "
                + "function cpm; value 1 } } }", "'1000' names no registered layer");
            assertRejected("filter { extractor { layer \"TEAM_\" + 2 } }", "'TEAM_2' names no registered layer");
            // Folded as the compiled code computes it: promotion to double, a leading zero octal.
            assertRejected("filter { extractor { layer \"K\" + (8L + 0.5) + \"S\" } }",
                "'K8.5S' names no registered layer");
            assertNotNull(compile("filter { extractor { layer \"K\" + 010 + \"S\" } }"));
        }
    }

    @Nested
    class Logging {
        @Test
        void aTrailingThrowableIsLoggedAsTheException() throws Exception {
            final List<LogEvent> events = capture();
            run("filter { log warn \"failed on {}\", span.name, failure()\n extractor { service \"x\" } }", "n");
            assertEquals("failed on n", events.get(0).getMessage().getFormattedMessage());
            assertEquals("boom", events.get(0).getThrown().getMessage());
            // A value of unknown type may be one; a String is not.
            assertNotNull(compile("filter { log warn \"failed\", thing()\n extractor { service \"x\" } }"));
            assertRejected("filter { log warn \"failed on {}\", span.name, span.name }",
                "the argument after the placeholders is logged as the exception, so it is a Throwable, not a String");
            assertRejected("filter { log warn \"failed\", span.name, failure() }",
                "has 0 {} placeholder(s) and 2 argument(s)");
        }

        @Test
        void anEscapedPlaceholderBesideATrailingExceptionIsText() throws Exception {
            final List<LogEvent> events = capture();
            run("filter { log warn \"prefix \\{}\", failure()\n extractor { service \"x\" } }", "n");
            assertEquals("prefix {}", events.get(0).getMessage().getFormattedMessage());
            assertEquals("boom", events.get(0).getThrown().getMessage());
        }

        @Test
        void aTrailingArgumentOfUnknownTypeMustBeAThrowableWhenLogged() throws Exception {
            final List<LogEvent> events = capture();
            // A String after the placeholders is no exception to log.
            final Exception e = assertThrows(IllegalArgumentException.class,
                () -> run("filter { log warn \"sampling failed\", labels().get(\"reason\")\n"
                    + " extractor { service \"x\" } }", "n"));
            assertTrue(e.getMessage().contains("a java.lang.String is none; add a {} for it"), e.getMessage());
            run("filter { def failed = failure() as java.lang.Object\n log warn \"failed\", failed\n"
                + " extractor { service \"x\" } }", "n");
            assertEquals("boom", events.get(0).getThrown().getMessage());
        }

        @Test
        void aLogMessageTakesOneArgumentPerPlaceholder() throws Exception {
            assertRejected("filter { log info \"one {}\", 1, 2 }", "has 1 {} placeholder(s) and 2 argument(s)");
            // A placeholder no argument fills prints as it is, as SLF4J leaves it.
            final List<LogEvent> events = capture();
            run("filter { log info \"literal \\{} and {}\", span.name\n extractor { service \"x\" } }", "n");
            assertEquals("literal {} and n", events.get(0).getMessage().getFormattedMessage());
            run("filter { log info \"expected \\{}\"\n extractor { service \"x\" } }", "n");
            assertEquals("expected {}", events.get(1).getMessage().getFormattedMessage());
            run("filter { log info \"{} and {}\", 1\n extractor { service \"x\" } }", "n");
            assertEquals("1 and {}", events.get(2).getMessage().getFormattedMessage());
            // Four backslashes before {}, with nothing to format, print as two, as the formatter halves the run.
            run("filter { log info \"\\\\\\\\\\\\\\\\{}\"\n extractor { service \"x\" } }", "n");
            assertEquals("\\\\{}", events.get(3).getMessage().getFormattedMessage());
        }
    }

    @Nested
    class GeneratedCodeFailures {
        @Test
        void generatedCodeTheJvmRefusesFailsTheRuleByName() {
            // A method on the literal null fails the rule's compile, naming it.
            final Exception e = assertThrows(IllegalArgumentException.class,
                () -> compile("filter { extractor { service (null).toString() } }"));
            assertTrue(messages(e).contains("literal null"), messages(e));
        }

        @Test
        void codeTheVerifierRefusesFailsTheRuleByNameWithItsCause() {
            final TALClassGenerator generator = new TALClassGenerator(ClassPool.getDefault(), new RefusingLoader());
            generator.setClassNameHint("strictness_refused");
            generator.setInputType(TestNestedSpanInput.class);
            generator.setInputAlias("test");
            generator.setFunctionRegistry(functions);
            generator.setSourceRef(DslSourceRef.ofRule("tal/test-strictness.yaml", 1));
            final Exception e = assertThrows(IllegalArgumentException.class,
                () -> generator.compile("filter { extractor { service \"x\" } }"));
            assertTrue(e.getMessage().contains("TAL rule strictness_refused")
                && e.getMessage().contains("generated code that does not compile or load"), e.getMessage());
            assertInstanceOf(VerifyError.class, e.getCause());
        }
    }

    @Nested
    class RateLimitIds {
        @Test
        void aRateLimitPlaceholderHoldsOneValue() throws Exception {
            // A comparison is no value an id holds.
            assertRejected("filter { persist { sampler { rateLimit(\"r-${span.name == 'x'}\") { rpm 1 } } } }",
                "holds one value");
            // A brace in a string literal of the expression is text.
            assertNotNull(compile("filter { persist { sampler { rateLimit(\"r-${meta.endpoint.split('[{]')[0]}\") "
                + "{ rpm 1 } } } }"));
            // Text that closes the parser's wrapper would add statements of its own.
            assertRejected("filter { persist { sampler { rateLimit(\"r-${span.name) { abort {} } if (span.name}\") "
                + "{ rpm 1 } } } }", "holds one value");
        }

        @Test
        void aPlaceholderMayHoldEscapedQuotes() throws Exception {
            assertNotNull(compile("filter { persist { sampler { rateLimit(\"r-${labels().get(\\\"reason\\\")}\") {"
                + " rpm 1 } } } }"));
        }

        @Test
        void anEscapedDollarKeepsAPlaceholderText() throws Exception {
            assertNotNull(compile("filter { persist { sampler { rateLimit(\"r-\\${span.name}\") { rpm 1 } } } }"));
        }
    }

    // ==================== Harness ====================

    private void assertRejected(final String dsl, final String expected) {
        final Exception e = assertThrows(Exception.class, () -> compile(dsl), dsl);
        assertTrue(messages(e).contains(expected), "expected <" + expected + "> in: " + messages(e));
    }

    private static String messages(final Throwable e) {
        final StringBuilder all = new StringBuilder();
        for (Throwable t = e; t != null; t = t.getCause()) {
            all.append(t.getMessage()).append(" | ");
        }
        return all.toString();
    }

    private TalExpression compile(final String dsl) throws Exception {
        final TALClassGenerator generator = new TALClassGenerator();
        generator.setClassNameHint("strictness_" + Math.abs(dsl.hashCode()));
        generator.setInputType(TestNestedSpanInput.class);
        generator.setInputAlias("test");
        generator.setFunctionRegistry(functions);
        generator.setSourceRef(DslSourceRef.ofRule("tal/test-strictness.yaml", 1));
        generator.setClassOutputDir(DslClassOutput.unitTestDir("tal"));
        return generator.compile(dsl);
    }

    private String run(final String dsl, final String spanName) throws Exception {
        return execute(dsl, spanName).getMeta().getService();
    }

    private ExecutionContext execute(final String dsl, final String spanName) throws Exception {
        final TestNestedSpanInput input = new TestNestedSpanInput();
        input.setName(spanName);
        final ExecutionContext ctx = new ExecutionContext();
        ctx.init(SpanMetadata.builder().service("").endpoint("/a/b").latency(5).build(), input, Collections.emptyMap(),
            Collections.emptyMap());
        compile(dsl).execute(filterSpec, ctx);
        return ctx;
    }

    private List<LogEvent> capture() {
        previousLevel = LogManager.getLogger(SCRIPT_LOGGER).getLevel();
        Configurator.setLevel(SCRIPT_LOGGER, Level.INFO);
        appender = new CapturingAppender();
        appender.start();
        ((Logger) LogManager.getLogger(SCRIPT_LOGGER)).addAppender(appender);
        return appender.events;
    }

    /**
     * A function that returns a Throwable, as a module's could.
     */
    public static final class FailureFunctions implements TALFunctionProvider {
        @Override
        public String module() {
            return "test-module";
        }

        @Override
        public boolean supportsInputType(final String inputType) {
            return true;
        }

        public IllegalStateException failure() {
            return new IllegalStateException("boom");
        }

        public Map<String, String> labels() {
            return Map.of("delimiter", "/", "reason", "timeout");
        }

        public String flag(final boolean on) {
            return on ? "on" : "off";
        }
    }

    /**
     * A loader whose verifier refuses every class it is handed.
     */
    private static final class RefusingLoader extends ClassLoader implements BytecodeClassDefiner {
        @Override
        public Class<?> defineClass(final String className, final byte[] bytecode) {
            throw new VerifyError("Bad type on operand stack in " + className);
        }
    }

    private static final class CapturingAppender extends AbstractAppender {
        private final List<LogEvent> events = Collections.synchronizedList(new ArrayList<>());

        private CapturingAppender() {
            super("tal-strictness-capture", null, null, true, Property.EMPTY_ARRAY);
        }

        @Override
        public void append(final LogEvent event) {
            events.add(event.toImmutable());
        }
    }
}
