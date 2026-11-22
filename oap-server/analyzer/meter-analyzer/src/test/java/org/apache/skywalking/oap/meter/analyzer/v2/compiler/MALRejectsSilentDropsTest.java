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
 *
 */

package org.apache.skywalking.oap.meter.analyzer.v2.compiler;

import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.Map;
import javassist.ClassPool;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalFilter;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalRuntimeHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALCompileAssertions.rejects;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Input a compiler could accept while dropping part of it: each does what it says, or fails at compile time with a
 * message instead of compiling a rule that runs without it.
 */
class MALRejectsSilentDropsTest {

    private MALClassGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MALClassGenerator(new ClassPool(true));
    }

    @Test
    void anAssignmentTargetDeeperThanAMapKeyIsRejected() {
        rejects("Cannot assign to", () -> generator.compile("t", "m.tag({ tags -> tags.foo.bar = 'x' })"));
        rejects("Cannot assign to", () -> generator.compile("t", "m.tag({ tags -> tags.labels['k'] = 'v' })"));
    }

    @Test
    void aSecondElseIsRejected() {
        rejects("parsing failed", () -> generator.compile("t",
            "m.tag({ tags -> if (tags.a == 'x') { tags.r = '1' } else if (tags.a == 'y') { tags.r = '2' } "
                + "else { tags.r = '3' } else { tags.r = '4' } })"));
    }

    @Test
    void aRegexLikeTextInsideAStringOrCommentIsLeftAlone() throws Exception {
        assertEquals(1, run("m.tagMatch('url', 'GET,/api/.*')", "url", "GET,/api/v1").samples.length);
        assertEquals(1, run("m.tagEqual('env', 'prod' /* don't, /x/ */)", "env", "prod").samples.length);
    }

    @Test
    void aConstantSubExpressionIsAScalar() throws Exception {
        assertEquals(-10.0, run("m * -1").samples[0].getValue(), 0.001);
        assertEquals(12.0, run("m + (1 + 1)").samples[0].getValue(), 0.001);
        assertEquals(60.0, run("m * (2 * 3)").samples[0].getValue(), 0.001);
        assertEquals(11.0, run("m - -1").samples[0].getValue(), 0.001);
        assertEquals(-10.0, run("m.multiply(-1)").samples[0].getValue(), 0.001);
        assertEquals(12.0, run("m.plus(1 + 1)").samples[0].getValue(), 0.001);
    }

    @Test
    void constantsAreComputedAsGroovyDoes() throws Exception {
        // the Long meets the sample family as a double
        assertEquals(10 * 9007199254740992.0, run("m * 9007199254740993").samples[0].getValue(), 1);
        rejects("holds exactly", () -> generator.compile("t", "m.tag({ tags -> tags.r = 'v' + 9007199254740993 })"));
        assertEquals(9.007199254740992E16, run("m * 9007199254740992").samples[0].getValue(), 1);
        // exact, where a double intermediate rounds 9007199254740993 to ...992
        assertEquals(20.0, run("m * (9007199254740991 + 2 - 9007199254740991)").samples[0].getValue(), 0.001);
        assertEquals(25.0, run("m * (10 / 4)").samples[0].getValue(), 0.001);
        rejects("no exact decimal", () -> generator.compile("t", "m * (1 / 3)"));
        rejects("Division by zero", () -> generator.compile("t", "m * (1 / 0)"));
        rejects("overflows int", () -> generator.compile("t", "m * (2147483647 + 1)"));
        rejects("overflows", () -> generator.compile("t", "m * -(-2147483647 - 1)"));
        rejects("constants only", () -> generator.compile("t", "1 + 2"));
    }

    @Test
    void timeTakesNoArgumentsOrCalls() {
        rejects("time() takes no arguments", () -> generator.compile("t", "m - time(100)"));
        rejects("time() takes no arguments", () -> generator.compile("t", "m - time().foo()"));
    }

    @Test
    void aScalarOfTimeIsComputedWhenTheExpressionRuns() throws Exception {
        final SampleFamily now = generator.compile("t", "m - time() * 1000").run(input());
        assertTrue(now.samples[0].getValue() < 0);
        generator.compile("t", "m.minus(time())");
        assertThrows(ArithmeticException.class, () -> MalRuntimeHelper.scalarDivide(1, 0));
    }

    @Test
    void aClassNameTakesNoIndexAndReadsOnlyListedStaticFields() throws Exception {
        for (final String index : new String[] {"Arrays[0]", "Integer[0]", "String[0]", "java.util.Arrays[0]",
            "ProcessRegistry[0]"}) {
            rejects("takes no index; an index reads a list, an array or a text",
                () -> generator.compile("t", "m.tag({ tags -> tags.r = " + index + " })"));
        }
        // a static field the list names reads; any other is refused
        rejects("Integer.MAX_VALUE is not supported in a MAL closure. The static fields are "
            + "[ProcessRegistry.LOCAL_VIRTUAL_PROCESS, ProcessRegistry.REMOTE_VIRTUAL_PROCESS]",
            () -> generator.compile("t", "m.tag({ tags -> tags.r = String.valueOf(Integer.MAX_VALUE) })"));
        assertEquals("UNKNOWN_REMOTE", run("m.tag({ tags -> tags.r = ProcessRegistry.REMOTE_VIRTUAL_PROCESS })")
            .samples[0].getLabels().get("r"));
        // an index on a value, what a call returns or a constant holds, reads that value
        assertEquals("b", run("m.tag({ tags -> tags.r = Arrays.asList('a', 'b')[1] })").samples[0].getLabels()
            .get("r"));
        assertEquals("4", run("m.tag({ tags -> tags.r = String.valueOf(42)[0] })").samples[0].getLabels().get("r"));
        assertEquals("U", run("m.tag({ tags -> tags.r = ProcessRegistry.LOCAL_VIRTUAL_PROCESS[0] })").samples[0]
            .getLabels().get("r"));
    }

    @Test
    void aClassNameCallsOnlyItsListedStaticMethods() {
        rejects("Integer.toString(...) is not supported in a MAL closure",
            () -> generator.compile("t", "m.tag({ t -> t.r = Integer.toString() })"));
        rejects("Integer.size(...) is not supported in a MAL closure",
            () -> generator.compile("t", "m.tag({ t -> t.r = Integer.size() })"));
    }

    @Test
    void anArrayACollectionOrAMapTakesNoPartInATextPlus() {
        rejects("A String[] takes no part in a text +",
            () -> generator.compile("t", "m.tag({ tags -> tags.r = 'x' + tags.k.split(',') })"));
        rejects("takes no part in a text +",
            () -> generator.compile("t", "m.tag({ tags -> tags.r = 'x' + Arrays.asList(tags.k, tags.j) })"));
    }

    @Test
    void aPropertyOfAValueReadsItsGetter() throws Exception {
        // whatever the receiver expression: decorate's bean, a local holding it, a grouped value; an array its length
        final String decorate = "m.service(['svc'], Layer.GENERAL).decorate({ me -> %s })";
        assertDoesNotThrow(() -> generator.compile("t",
            String.format(decorate, "me.attr0 = String.valueOf(me.serviceName.split('/').length)")));
        assertDoesNotThrow(() -> generator.compile("t", String.format(decorate, "def e = me\n me.attr0 = e.serviceName")));
        assertDoesNotThrow(() -> generator.compile("t", String.format(decorate, "me.attr0 = (me).serviceName")));
        // a getter the list does not name, a static field read through a value, a class used as a value: refused
        rejects("String.getBytes(...) is not supported in a MAL closure",
            () -> generator.compile("t", "m.tag({ tags -> tags.r = String.valueOf(tags.k.bytes.length) })"));
        rejects("Integer.MAX_VALUE reads no property",
            () -> generator.compile("t", "m.tag({ tags -> tags.r = String.valueOf(Integer.valueOf(1).MAX_VALUE) })"));
        rejects("reads no property", () -> generator.compile("t",
            String.format(decorate, "me.attr0 = String.valueOf((me.layer).GENERAL)")));
        rejects("The class ProcessRegistry is no value",
            () -> generator.compile("t", "m.tag({ tags -> def x = ProcessRegistry\n tags.r = 'a' })"));
    }

    @Test
    void safeNavigationThatWouldNotBeHonouredIsRejected() {
        rejects("?. after", () -> generator.compile("t",
            "m.tag({ tags -> String x = tags.missing; tags.r = x?.toUpperCase() })"));
        rejects("?. after", () -> generator.compile("t", "m.tag({ tags -> tags.r = (tags.missing)?.toUpperCase() })"));
        rejects("after the map lookup", () -> generator.compile("t",
            "m.tag({ tags -> tags.r = 'v' + tags.a?.bytes })"));
    }

    @Test
    void interpolationIsGroovys() throws Exception {
        rejects("Unsupported interpolation",
            () -> generator.compile("t", "m.tag({ tags -> tags.r = \"v-${tags.a ?: 'none'}\" })"));
        rejects("Unterminated", () -> generator.compile("t", "m.tag({ tags -> tags.r = \"a${tags.xy\" })"));
        rejects("Unbraced interpolation", () -> generator.compile("t", "m.tag({ tags -> tags.r = \"v-$tags.a\" })"));
        rejects("Interpolation is not supported", () -> generator.compile("t", "m.tagEqual(\"k\", \"${v}\")"));
        rejects("Interpolation is not supported",
            () -> generator.compile("t", "m.tag({ tags -> [\"${tags.k}\": 'v'] })"));
        assertEquals("${tags.a}",
            run("m.tag({ tags -> tags.r = '${tags.a}' })", "a", "y").samples[0].getLabels().get("r"),
            "single-quoted strings are not interpolated");
        assertEquals("v-y", run("m.tag({ tags -> tags.r = \"v-${tags.a}\" })", "a", "y").samples[0].getLabels().get("r"));
    }

    @Test
    void escapesReadAsMalAlwaysReadThem() throws Exception {
        // \n \r \t, the quotes, \\ and \$ are the characters; any other escape stays as written, so a regex gets it
        assertEquals("a\tb", run("m.tag({ tags -> tags.r = 'a\\tb' })").samples[0].getLabels().get("r"));
        assertEquals("\\101", run("m.tag({ tags -> tags.r = '\\101' })").samples[0].getLabels().get("r"));
        assertEquals("a\\bc", run("m.tag({ tags -> tags.r = 'a\\bc' })").samples[0].getLabels().get("r"));
    }

    @Test
    void anOutOfRangeArgumentForAWholeNumberParameterIsRejected() throws Exception {
        rejects("not a whole number", () -> generator.compile("t", "m.test::times(2.5)"));
        rejects("not a whole number", () -> generator.compile("t", "m.test::times(4 / 2)"));
        assertEquals(-30.0, generator.compile("t", "m.test::times(-3)").run(input()).samples[0].getValue(), 0.001);
        assertEquals(30.0, generator.compile("t", "m.test::times(1 + 2)").run(input()).samples[0].getValue(), 0.001);
        // a double reads it as 2; Groovy passes a BigDecimal, which no int parameter takes
        rejects("not a whole number", () -> generator.compile("t", "m.test::times(2.0000000000000001)"));
        rejects("out of the range", () -> generator.compile("t", "m.test::times(2147483648)"));
        assertEquals(30.0, generator.compile("t", "m.test::times(3)").run(input()).samples[0].getValue(), 0.001);
    }

    @Test
    void greaterOrEqualAndLessOrEqualRun() throws Exception {
        final MalFilter atLeastTwo = generator.compileFilter("{ tags -> tags.a.length() >= 2 }");
        assertTrue(atLeastTwo.test(Map.of("a", "ab")));
        assertFalse(atLeastTwo.test(Map.of("a", "a")));
        assertTrue(generator.compileFilter("{ tags -> tags.a.length() <= 1 }").test(Map.of("a", "a")));
    }

    @Test
    void aFilterReturnsItsLastExpression() throws Exception {
        final MalFilter filter = generator.compileFilter("{ tags -> String j = tags.job; j }");
        assertTrue(filter.test(Map.of("job", "a")));
        assertFalse(filter.test(Map.of("job", "")));
        assertFalse(generator.compileFilter("{ tags -> tags.job }").test(Map.of("job", "")), "Groovy truth");
        // a final if returns what the branch taken ends with
        final MalFilter branches = generator.compileFilter("{ tags -> if (tags.job == 'a') { true } else { false } }");
        assertTrue(branches.test(Map.of("job", "a")));
        assertFalse(branches.test(Map.of("job", "b")));
    }

    @Test
    void whatGroovyWouldComputeDifferentlyIsRejected() {
        rejects("Ambiguous negation", () -> generator.compile("t", "m.tag({ t -> if (!t.a == t.b) { t.r = 'x' } })"));
        rejects("computes a value nothing uses",
            () -> generator.compile("t", "m.tag({ t -> ['a': '1']; ['b': '2'] })"));
        rejects("Ambiguous negation", () -> generator.compile("t", "m.tag({ t -> if (!t.a ? t.b : t.c) { t.r = 'x' } })"));
        rejects("must end with the map", () -> generator.compile("t",
            "m.instance(['a'], '', ['b'], '', Layer.GENERAL, { t -> t.x == 'y' })"));
    }

    @Test
    void whatWouldBeEvaluatedEagerlyOrTwiceIsRejected() {
        rejects("map parameter itself", () -> generator.compile("t", "m.tag({ t -> t.r = t?.toString() })"));
    }

    @Test
    void aStringTimesANumberOrAgainstANumberIsRejected() {
        rejects("string times a number", () -> generator.compile("t", "m.tag({ t -> t.r = 'b' * 2 })"));
        rejects("string times a number", () -> generator.compile("t", "m.tag({ t -> t.r = t.a * 3 })"));
        rejects("is not a number", () -> generator.compileFilter("{ t -> 'abc' < 5 }"));
    }

    @Test
    void aReturnedLabelMapOfAnotherKindIsRejected() {
        rejects("a String[] is not", () -> generator.compile("t", "m.tag({ t -> ['parts': t.path.split('/')] })"));
    }

    @Test
    void aScalarDividedByZeroIsRejected() {
        rejects("Division by zero", () -> generator.compile("t", "m.valueLess(time() / 0)"));
    }

    @Test
    void whatGroovyHasNoPlusForIsRejected() {
        rejects("has no meaning in Groovy", () -> generator.compile("t", "m.tag({ t -> t.r = (t.a == 'b') + '!' })"));
        rejects("has no meaning in Groovy", () -> generator.compile("t", "m.tag({ t -> t.r = true + 'x' })"));
        rejects("+ on an array", () -> generator.compileFilter("{ t -> String.valueOf(t.aliases.split(',') + "
            + "t.default_alias).contains(t.target) }"));
        rejects("+ on an array", () -> generator.compileFilter("{ t -> def aliases = t.aliases.split(',') + "
            + "t.default_alias\n aliases.contains(t.target) }"));
    }

    @Test
    void aLabelValueOfAnotherKindIsRejected() {
        rejects("a String[] is not", () -> generator.compile("t", "m.tag({ t -> t.l = t.a.split(',') })"));
        rejects("a Map is not", () -> generator.compile("t", "m.tag({ t -> t.put('m', ['a': 'b']) })"));
    }

    @Test
    void aClosureNumberGroovyHoldsOtherwiseIsRejected() throws Exception {
        assertEquals("v2.5", run("m.tag({ t -> t.r = 'v' + 2.50 })").samples[0].getLabels().get("r"),
            "a decimal literal is a double");
        rejects("beyond a long", () -> generator.compile("t", "m.tag({ t -> t.r = 'v' + 9223372036854775808 })"));
        rejects("no octal number", () -> generator.compile("t", "m * 08"));
        rejects("no octal number", () -> generator.compile("t", "m.tag({ t -> t.r = 'v' + 09 })"));
    }

    @Test
    void whatTheGrammarWouldGroupOtherwiseIsRejected() {
        rejects("before in", () -> generator.compileFilter("{ t -> false == t.env in ['prod'] }"));
        rejects("parenthesize", () -> generator.compileFilter("{ t -> t.a == 'x' || t.b == 'y' ? false : true }"));
        rejects("parenthesize", () -> generator.compileFilter("{ t -> t.a == 'x' && t.b ? true : false }"));
        rejects("next line", () -> generator.compile("t", "m.tag({ t -> return\n ['only': t.a] })"));
    }

    @Test
    void numbersWithoutASampleFamilyAreRejected() {
        rejects("method on a number", () -> generator.compile("t", "m * (2).multiply(3)"));
        rejects("method on a number", () -> generator.compile("t", "(1).plus(2)"));
        rejects("no sample family", () -> generator.compile("t", "count(1 + 2)"));
    }

    @Test
    void histogramPercentileTakesWholeRanksAfterHistogram() {
        rejects("whole number from 0 to 100", () -> generator.compile("t",
            "m.histogram().histogram_percentile([99.9])"));
        rejects("one list of numbers", () -> generator.compile("t",
            "m.histogram().histogram_percentile(['95', '99'])"));
        rejects("would be read as", () -> generator.compile("t",
            "m.histogram().histogram_percentile([99.00000000000000001])"));
        rejects("histogram() before it", () -> generator.compile("t",
            "m.histogram_percentile([99]).service(['svc'], Layer.GENERAL)"));
    }

    @Test
    void dollarsGroovyReadsOtherwiseAreRejected() {
        rejects("slashy string", () -> generator.compile("t", "m.tag({ t -> t.r = t.a.replaceAll(/\\$p/, 'Y') })"));
        // a $ that starts no interpolation is the character, as the rules have always read it
        assertDoesNotThrow(() -> generator.compile("t", "m.tag({ t -> t.r = \"cost$5\" })"));
    }

    @Test
    void aClosureTakesTheParametersItsCallPasses() {
        rejects("takes 1 distinct", () -> generator.compile("t", "m.tag({ a, b -> ['id': a.id] })"));
        rejects("takes one parameter", () -> generator.compileFilter("{ -> false }"));
        rejects("takes 2 distinct parameters", () -> generator.compile("t",
            "m.forEach(['a'], { e -> e.length() })"));
        rejects("closure parameter", () -> generator.compile("t", "m.tag({ t -> t = ['env': 'prod']; return null })"));
    }

    @Test
    void constantsKeepTheirWrittenValue() throws Exception {
        generator.compile("t", "m.test::plusLong(9223372036854775807)");
        assertEquals(1.0E-15, run("m * (1.0000000000000001 - 1)").samples[0].getValue(), 1e-20);
        assertEquals(10.0 + 9007199254740994.0,
            generator.compile("t", "m.test::plusLong(9007199254740994)").run(input()).samples[0].getValue(), 1);
        assertEquals(13.0, generator.compile("t", "m.test::plusBoxed(3)").run(input()).samples[0].getValue(), 0.001);
    }

    @Test
    void aMetricInAFunctionsChainIsAnInput() throws Exception {
        final String exp = MALScriptParser.injectExpPrefix("count(m, ['env']).div(MAX.sum(['env']))",
            "tagEqual('env', 'prod')");
        assertTrue(generator.compile("t", exp).metadata().getSamples().contains("MAX"));
    }

    @Test
    void slashyInterpolationIsRejected() {
        rejects("slashy string", () -> generator.compile("t", "m.tag({ t -> t.r = t.b.replaceAll(/$p/, 'Y') })"));
        rejects("slashy string", () -> generator.compile("t", "m.tag({ t -> if (t.b =~ /${t.p}/) { t.r = 'y' } })"));
    }

    @Test
    void expPrefixFindsTheMetricsTheCompilerReads() throws Exception {
        final String prefix = "tagEqual('env', 'prod')";
        final String parenthesised = MALScriptParser.injectExpPrefix("m.sum(['s']).downsampling((MAX))", prefix);
        assertEquals(parenthesised.indexOf("tagEqual"), parenthesised.lastIndexOf("tagEqual"), parenthesised);
        generator.compile("t", parenthesised);
        // Token offsets count code points: the prefix goes after the metric, not inside the comment.
        assertEquals("/*\uD83D\uDE00\uD83D\uDE00abc*/(MAX.tagEqual('env', 'prod'))",
            MALScriptParser.injectExpPrefix("/*\uD83D\uDE00\uD83D\uDE00abc*/MAX", prefix));
        generator.compile("t", MALScriptParser.injectExpPrefix("m.tagMatch('a', /^abc/)", prefix));
    }

    @Test
    void aMetricNamedLikeADownsamplingTypeGetsTheExpPrefix() {
        final String prefixed = MALScriptParser.injectExpPrefix(
            "MAX.sum(['s']) + other.sum(['s'])", "tagEqual('a', 'b')");
        assertNotEquals(prefixed.indexOf("tagEqual"), prefixed.lastIndexOf("tagEqual"), prefixed);
        final String counted = MALScriptParser.injectExpPrefix("count(MAX, ['s'])", "tagEqual('a', 'b')");
        assertTrue(counted.contains("tagEqual"), counted);
        final String argument = MALScriptParser.injectExpPrefix("m.sum(['s']).downsampling(MAX)", "tagEqual('a', 'b')");
        assertEquals(argument.indexOf("tagEqual"), argument.lastIndexOf("tagEqual"), argument);
    }

    private SampleFamily run(final String expression, final String... labels) throws Exception {
        return generator.compile("t", expression).run(input(labels));
    }

    private static Map<String, SampleFamily> input(final String... labels) {
        final Map<String, String> map = new HashMap<>();
        for (int i = 0; i < labels.length; i += 2) {
            map.put(labels[i], labels[i + 1]);
        }
        return Map.of("m", SampleFamilyBuilder.newBuilder(
            Sample.builder().name("m").labels(ImmutableMap.copyOf(map)).value(10.0).build()).build());
    }
}
