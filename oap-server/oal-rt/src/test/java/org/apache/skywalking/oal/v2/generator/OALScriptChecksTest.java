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

package org.apache.skywalking.oal.v2.generator;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtField;
import javassist.CtNewMethod;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.dsl.debug.DSLDebugCodegenSwitch;
import org.apache.skywalking.oal.v2.generator.custom.Endpoint;
import org.apache.skywalking.oal.v2.metadata.FilterMatchers;
import org.apache.skywalking.oal.v2.generator.custom.IndexedTags;
import org.apache.skywalking.oal.v2.generator.custom.LayersReady;
import org.apache.skywalking.oal.v2.generator.custom.MQAccess;
import org.apache.skywalking.oal.v2.generator.optional.Anchor;
import org.apache.skywalking.oal.v2.generator.custom.Origin;
import org.apache.skywalking.oal.v2.model.FunctionCall;
import org.apache.skywalking.oal.v2.model.MetricDefinition;
import org.apache.skywalking.oal.v2.model.SourceReference;
import org.apache.skywalking.oap.server.core.analysis.metrics.ChecksArrayMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.ChecksByteLimitMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.ChecksFloorMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.ChecksMeasuredMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.ChecksMeasurement;
import org.apache.skywalking.oap.server.core.analysis.metrics.ChecksModeMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.ChecksStreamNameMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.ChecksWholeSourceMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.CountMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.HistogramMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabelAvgMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.LabelCountMetrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.PercentileMetrics2;
import org.apache.skywalking.oap.server.core.oal.rt.OALCompileException;
import org.apache.skywalking.oap.server.core.source.CiliumMetrics;
import org.apache.skywalking.oap.server.core.source.CiliumService;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.RequestType;
import org.apache.skywalking.oap.server.core.source.Service;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every part of an OAL statement either takes effect or fails the compile; none is dropped or rewritten in silence.
 * Metric names carry the {@code chk_} prefix: generated classes share a package with every other OAL test.
 */
public class OALScriptChecksTest {

    /**
     * A form whose generated code does not compile fails the compile: Javassist rejects it, or the JVM's verifier when
     * the compiler links the class.
     */
    private static void assertCompileFails(String script, String sourcePackage) {
        assertThrows(OALCompileException.class, () -> OALScriptRunner.compile(script, sourcePackage));
    }

    private static void assertCompileFails(String script) {
        assertCompileFails(script, OALScriptRunner.SOURCE_PACKAGE);
    }

    private static void assertRejected(String script, String messagePart) {
        final IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class, () -> OALScriptRunner.enrich(script));
        assertTrue(e.getMessage().contains(messagePart), "message was: " + e.getMessage());
    }

    private static final String CUSTOM = "org.apache.skywalking.oal.v2.generator.custom.";

    private static void assertRejected(String script, String messagePart, String sourcePackage) {
        final IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class, () -> OALScriptRunner.enrich(script, sourcePackage));
        assertTrue(e.getMessage().contains(messagePart), "message was: " + e.getMessage());
    }

    private static Endpoint custom(String name, int latency) {
        return fill(new Endpoint(), name, latency);
    }

    private static Endpoint fill(Endpoint service, String name, int latency) {
        service.setName(name);
        service.setServiceName("svc");
        service.setLatency(latency);
        service.setTimeBucket(202611201200L);
        service.setOriginalTags(new HashMap<>());
        return service;
    }

    private static Service service(String name, int latency) {
        final Service service = new Service();
        service.setName(name);
        service.setLayer(Layer.GENERAL);
        service.setLatency(latency);
        service.setTimeBucket(202611201200L);
        service.setOriginalTags(new HashMap<>());
        service.setTags(new ArrayList<>());
        return service;
    }

    private static Map<String, Metrics> byClass(List<Metrics> metrics) {
        final Map<String, Metrics> byClass = new HashMap<>();
        for (Metrics m : metrics) {
            byClass.put(m.getClass().getSimpleName(), m);
        }
        return byClass;
    }

    private static Object field(Object target, String name) throws Exception {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                final Field field = c.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(target);
            } catch (NoSuchFieldException e) {
                // Declared further up.
            }
        }
        throw new NoSuchFieldException(name);
    }

    // Arguments

    @Test
    public void anAttributePathReadsOnlyTheSourcesAttributes() {
        // a JDK value on the way has no attributes: name.bytes would compile to getName().getBytes()
        assertRejected("chk_bytes = from(Service.name.bytes).count();",
                       "reads String.getBytes(), which is no attribute");
        assertRejected("chk_bytes_filter = from(Service.*).filter(name.empty == true).count();",
                       "which is no attribute");
        // Object's methods on an interface, getClass() included, are none either
        assertRejected("chk_class = from(Service.*).filter(tags.class.name != null).count();", "which is no attribute");
        // a JDK getter an application class inherits is the JDK type's, no attribute either
        assertRejected("chk_inherited = from(Endpoint.*).filter(indexTags.empty == true).count();",
                       "which is no attribute", CUSTOM);
    }

    @Test
    public void extraArgumentsAreRejected() {
        assertRejected("chk_count_cond = from(Service.*).count(status == true);",
                       "count() takes 0 argument(s), but is given 1: [status == true] would be ignored");
        assertRejected("chk_p2_extra = from(Service.latency).percentile2(10, 99);",
                       "percentile2() takes 1 argument(s), but is given 2: [99] would be ignored");
        assertRejected("chk_sum_attr = from(Service.latency).sum(httpResponseStatusCode);",
                       "sum() takes 0 argument(s)");
    }

    @Test
    public void missingArgumentsAreRejected() {
        // Left short of an argument, the call would go to a shorter overload, as ChecksWindowMetrics#combine(String).
        assertRejected("chk_percent_none = from(Service.*).percent();", "percent() needs argument 1, of type boolean");
        assertRejected("chk_apdex_one = from(Service.latency).apdex(name);",
                       "apdex() needs argument 2, of type boolean");
        assertRejected("chk_window_one = from(Service.*).chkWindow(name);",
                       "chkWindow() needs argument 2, of type int");
    }

    @Test
    public void argumentsOfTheWrongKindAreRejected() {
        assertRejected("chk_percent_attr = from(Service.*).percent(status);",
                       "Expected expression argument but got: status");
        // A string would be pasted into the call as Java code.
        assertRejected("chk_p2_string = from(Service.latency).percentile2(\"10\");",
                       "percentile2() argument 1 is the string \"10\", which a function argument cannot be");
        assertRejected("chk_p2_big = from(Service.latency).percentile2(3000000000);",
                       "3000000000 is out of the int range; a long is written with an L");
        // Left out of the call, a condition would leave Javassist to pick another overload, or none.
        assertRejected("chk_label_cond = from(Service.*).labelCount(status == true);",
                       "labelCount() argument 1 is of type String, not the condition status == true");
        assertRejected("chk_limit_cond = from(Service.*).labelCount(name, status == true);",
                       "labelCount() argument 2 is of type int, not the condition status == true");
        assertCompileFails("chk_label_int_attr = from(Service.*).labelCount(latency);");
    }

    @Test
    public void aStringLiteralArgumentIsRejected() {
        // It would run as the code it spells, and is no argument a function can be sure to take at its entrance.
        assertRejected("chk_label_literal = from(Service.*).labelCount(\"source.getEndpointName()\");",
                       "labelCount() argument 1 is the string \"source.getEndpointName()\", which a function argument "
                           + "cannot be: a string literal would run as the Java code it spells");
    }

    @Test
    public void anOptionalArgumentIsALiteral() throws Exception {
        final Map<String, Metrics> metrics = byClass(OALScriptRunner.compile(
            "chk_label_max_literal = from(Service.*).labelCount(name, 3);\n"
                + "chk_label_max_default = from(Service.*).labelCount(name);").dispatch(service("svc", 42)));
        assertEquals(3, field(metrics.get("ChkLabelMaxLiteralMetrics"), "maxLabelCount"));
        assertEquals(50, field(metrics.get("ChkLabelMaxDefaultMetrics"), "maxLabelCount"));
        // A limit per event would differ between the events merged into one metric, and is not serialized.
        assertRejected("chk_label_max_attr = from(Service.*).labelCount(name, latency);",
                       "labelCount() argument 2 configures the metric, so it is a literal, not the attribute latency");
        // labelAvg's limit is out of reach, as the grammar keeps it: it would not survive a remote merge.
        assertThrows(IllegalArgumentException.class,
                     () -> OALScriptRunner.enrich("chk_label_avg_max = from(Service.*).labelAvg(name, latency, 7);"));
    }

    @Test
    public void bucketWidthsArePositive() throws Exception {
        assertRejected("chk_p2_zero = from(Service.latency).percentile2(0);",
                       "percentile2() argument 1 is 0, but must be at least 1");
        assertRejected("chk_histogram_zero = from(Service.latency).histogram(0, 20);",
                       "histogram() argument 1 is 0, but must be at least 1");
        assertRejected("chk_histogram_overflow = from(Service.latency).histogram(1000000000, 3);",
                       "histogram(1000000000, 3): the last bucket, 3000000000, is out of the int range");
        assertEquals(2, OALScriptRunner.enrich("chk_histogram_ok = from(Service.latency).histogram(100, 0);\n"
                                                   + "chk_p2_ok = from(Service.latency).percentile2(1);").size());
        // Built through the model API, a literal may be an Integer; it is checked as a script's Long is.
        final MetricDefinition zero = MetricDefinition.builder()
            .name("chk_model_p2_zero").source(SourceReference.of("Service", "latency"))
            .aggregationFunction(FunctionCall.ofLiterals("percentile2", 0)).build();
        final IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> enricher().enrich(zero));
        assertTrue(e.getMessage().contains("percentile2() argument 1 is 0, but must be at least 1"), e.getMessage());
        assertNotNull(enricher().enrich(MetricDefinition.builder()
            .name("chk_model_histogram").source(SourceReference.of("Service", "latency"))
            .aggregationFunction(FunctionCall.ofLiterals("histogram", 100, 20)).build()));
    }

    @Test
    public void aBucketWidthMayComeFromTheSource() throws Exception {
        // A width stable per entity, as a custom source may give, comes from the source.
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_width_p2 = from(Endpoint.latency).percentile2(bucketWidth);\n"
                + "chk_width_histogram = from(Endpoint.latency).histogram(bucketWidth, 5);", CUSTOM);
        final Endpoint service = custom("svc", 37);
        service.setBucketWidth(10);
        final Map<String, Metrics> metrics = byClass(runner.dispatch(service));
        final PercentileMetrics2 percentile = (PercentileMetrics2) metrics.get("ChkWidthP2Metrics");
        assertEquals(10, percentile.getPrecision());
        assertEquals(1L, percentile.getDataset().get("3"));
        assertEquals(1L, ((HistogramMetrics) metrics.get("ChkWidthHistogramMetrics")).getDataset().get("30"));
    }

    private static MetricDefinitionEnricher enricher() {
        return new MetricDefinitionEnricher(OALScriptRunner.SOURCE_PACKAGE,
                                            "org.apache.skywalking.oap.server.core.source.oal.rt.metrics.");
    }

    @Test
    public void aCastIsSupportedInFromOnly() {
        // Elsewhere a value it cannot parse would stop the source's other metrics.
        assertRejected("chk_arg_cast = from(Service.*).labelAvg(name, (str->long)tag[\"size\"]);",
                       "labelAvg() argument 2 (str->long)tag[\"size\"]: a cast such as (str->long) is supported in "
                           + "from() only, not in a filter or a function argument");
        assertRejected("chk_filter_cast = from(Service.*).filter((str->long)tag[\"x.y\"] > 5).count();",
                       "filter((str->long)tag[\"x.y\"] > 5): a cast such as (str->long) is supported in from() only");
        assertRejected("chk_condition_cast = from(Service.*).percent((int)tag[\"x\"] == 1);",
                       "a cast such as (int) is supported in from() only");
    }

    @Test
    public void keyedFormsAPathSplitAtEachDotCannotReadAreRejected() {
        // A filter reads a keyed attribute as its whole attribute, or before the last segment of a dotted path.
        assertRejected("chk_nested_key = from(Endpoint.*).filter(origin.label[\"if-match\"] == \"\\\"v1\\\"\").count();",
                       "the keyed attribute origin.label[\"if-match\"] is not supported here: a filter reads one", CUSTOM);
        assertRejected("chk_nested_key_in = from(Endpoint.*).filter(origin.label[\"if-match\"] in [HttpCodes.ETAG])"
                           + ".count();", "is not supported here", CUSTOM);
        // A function argument reads one in a dotted path only, with a key holding no dot.
        assertRejected("chk_label_tag = from(Service.*).labelCount(tag[\"zone\"]);",
                       "labelCount() argument 1: the keyed attribute tag[\"zone\"] is not supported here: a function argument");
        assertRejected("chk_keyed_width = from(Endpoint.latency).percentile2(header[\"window\"]);", "is not supported here",
                       CUSTOM);
        assertRejected("chk_keyed_avg = from(Endpoint.*).labelAvg(name, header[\"cpu\"]);", "is not supported here", CUSTOM);
        assertRejected("chk_dotted_key = from(Endpoint.*).labelCount(origin.label[\"http.route\"]);", "is not supported here",
                       CUSTOM);
    }

    @Test
    public void aNumberOutOfTheIntRangeStaysALongForAKeyedGetter() throws Exception {
        // getCounter(CharSequence) returns an int the compile does not see; a bare 3000000000 would wrap to a negative.
        assertCompileFails("chk_counter_big = from(Endpoint.*).filter(counter[\"bytes\"] > 3_000_000_000L).count();",
                           CUSTOM);
    }

    @Test
    public void anArgumentNumberIsPastedAsWritten() throws Exception {
        // Written as is, for Javassist to pass to the overload it picks: labelCount has none for a long limit.
        assertCompileFails("chk_limit_long = from(Service.*).labelCount(name, 1L);");
        assertNotNull(OALScriptRunner.enrich("chk_count_long = from(Service.*).labelAvg(name, 5L);"));
        // A number without an L is an int, which the long count of labelAvg does not take.
        assertCompileFails("chk_count_int = from(Service.*).labelAvg(name, 1);");
        // ChecksByteLimitMetrics' entrance takes an int, and its long overload a long.
        final Map<String, Metrics> limits = byClass(OALScriptRunner.compile(
            "chk_byte_limit = from(Service.*).chkByteLimit(4096L);\n"
                + "chk_byte_limit_big = from(Service.*).chkByteLimit(3000000000L);").dispatch(service("svc", 1)));
        assertEquals(4096L, ((ChecksByteLimitMetrics) limits.get("ChkByteLimitMetrics")).getLimit());
        assertEquals(3000000000L, ((ChecksByteLimitMetrics) limits.get("ChkByteLimitBigMetrics")).getLimit());
        // Passed as written: Javassist reads 0644L as octal 420.
        final List<Metrics> modes = OALScriptRunner.compile("chk_mode = from(Service.*).chkMode(0644L);")
                                                    .dispatch(service("svc", 1));
        assertEquals(420L, ((ChecksModeMetrics) modes.get(0)).getMode());
    }

    @Test
    public void aNumberWithoutAnLIsAnInt() throws Exception {
        // Javassist reads it as an int, whatever the parameter: ChecksFloorMetrics' int overload would take it wrapped.
        assertRejected("chk_floor_big = from(Service.*).chkFloor(3000000000);",
                       "chkFloor() argument 1 3000000000 is out of the int range; a long is written with an L");
        // Below the entrance's least value, which the int overload passes it on to.
        assertRejected("chk_floor_low = from(Service.*).chkFloor(100);",
                       "chkFloor() argument 1 is 100, but must be at least 256");
        final List<Metrics> floors = OALScriptRunner.compile("chk_floor = from(Service.*).chkFloor(3000000000L);")
                                                     .dispatch(service("svc", 1));
        assertEquals(3000000000L, ((ChecksFloorMetrics) floors.get(0)).getSize());
    }

    @Test
    public void aDecoratorNameIsPastedAsWritten() throws Exception {
        // Pasted into a Java string as written, which reads the escape: no decorator is called Service\Decorator.
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_deco_escaped = from(Service.*).cpm().decorator(\"Service\\\\Decorator\");");
        assertThrows(RuntimeException.class, () -> runner.dispatch(service("svc", 1)));
    }

    @Test
    public void aStringNamingAConstantInAListIsNoConstant() {
        // "Layer.MESH" is the string it spells, not the constant, and a Layer never equals it.
        assertRejected("chk_layer_str = from(Service.*).filter(layer in [\"Layer.MESH\"]).count();",
                       "Service.layer is of type Layer, which never equals the string \"Layer.MESH\"");
        // Nor does it compile against a keyed attribute, whose type, here a List, the compile does not see.
        assertRejected("chk_header_values = from(Endpoint.*).filter(headerValues[\"Accept\"] in "
                           + "[\"application/vnd.api+json\"]).count();",
                       "the string \"application/vnd.api+json\" is compared only with a field whose type", CUSTOM);
    }

    @Test
    public void onlyTheStockHistogramTakesAStepAndANumberOfSteps() throws Exception {
        // ChecksRangeMetrics' own entrance takes the bounds of a range, whose product is no bucket.
        final List<Metrics> metrics = OALScriptRunner.compile(
            "chk_range = from(Service.latency).chkRange(50000, 100000);").dispatch(service("svc", 55000));
        assertEquals(1L, ((HistogramMetrics) metrics.get(0)).getDataset().get("5000"));
    }

    @Test
    public void aCastResultPassesOnlyToAParameterOfItsType() {
        // An int parsed for longAvg's long parameter fails the compile: no wider overload takes it instead.
        assertCompileFails("chk_cast_int_long = from((str->int)Service.tag[\"duration\"]).longAvg();");
        // A parsed long is not cut to an int parameter: Javassist rejects the call.
        assertCompileFails("chk_cast_narrow = from((str->long)Service.tag[\"latency_ns\"]).percentile2(10);");
    }

    @Test
    public void aNullComparisonTakesNoQuotesOff() throws Exception {
        // StringMatch#match(String, String) would take the quotes off a lone quote, and fail on it.
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_tag_null = from(Service.*).filter(tag[\"etag\"] == null).count();\n"
                + "chk_tag_null_after = from(Service.*).count();");
        final Service quote = service("svc", 1);
        quote.setOriginalTags(Collections.singletonMap("etag", "\""));
        assertEquals(Set.of("ChkTagNullAfterMetrics"), byClass(runner.dispatch(quote)).keySet());
        assertEquals(Set.of("ChkTagNullMetrics", "ChkTagNullAfterMetrics"),
                     byClass(runner.dispatch(service("svc", 1))).keySet());
    }

    @Test
    public void aConstantThatMayBeNullIsCompared() throws Exception {
        // UnavailableValue.UNKNOWN is a null String, which an unset RequestType equals.
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_unknown_type = from(Endpoint.*).filter(type == UnavailableValue.UNKNOWN).count();", CUSTOM);
        assertEquals(1, runner.dispatch(custom("svc", 1)).size());
        final Endpoint http = custom("svc", 1);
        http.setType(RequestType.HTTP);
        assertEquals(0, runner.dispatch(http).size());
    }

    @Test
    public void aConstantOfAClassThatCannotLoadIsCopiedByJavassist() throws Exception {
        // OptionalHttpCodes implements an interface that is not on the classpath; Javassist copies its constant. It is
        // written beside the source in test.apache.skywalking, which no scan of the OAP reads, and removed after.
        final Class<? extends Endpoint> sourceClass = test.apache.skywalking.oal.unloadable.Endpoint.class;
        final ClassPool pool = OALScriptRunner.classPool();
        final String unloadable = sourceClass.getPackage().getName() + ".";
        final CtClass codes = pool.makeClass(unloadable + "OptionalHttpCodes");
        codes.addInterface(pool.makeInterface(unloadable + "MissingHttpIntegration"));
        codes.addField(CtField.make("public static final String OK = \"api\";", codes));
        final File classes = new File(sourceClass.getProtectionDomain().getCodeSource().getLocation().toURI());
        codes.writeFile(classes.getPath());
        try {
            final OALScriptRunner runner = OALScriptRunner.compile(
                "chk_optional_http = from(Endpoint.*).filter(name == OptionalHttpCodes.OK).count();", unloadable);
            assertEquals(1, runner.dispatch(fill(sourceClass.getDeclaredConstructor().newInstance(), "api", 1)).size());
            assertEquals(0, runner.dispatch(fill(sourceClass.getDeclaredConstructor().newInstance(), "web", 1)).size());
        } finally {
            Files.deleteIfExists(
                new File(classes, (unloadable + "OptionalHttpCodes").replace('.', '/') + ".class").toPath());
        }
    }

    @Test
    public void aListNeverEqualsAString() {
        // in compares the whole list; contain looks in it.
        assertRejected("chk_tags_in = from(Service.*).filter(tags in [\"http.method:GET\"]).count();",
                       "Service.tags is a List, which never equals the string \"http.method:GET\"; contain looks for a "
                           + "string in it");
    }

    @Test
    public void aKeyedGetterIsTheOneJavassistCalls() throws Exception {
        // Javassist calls the source's own RequestType getKind(CharSequence), not the inherited String getKind(String).
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_kind = from(Endpoint.*).filter(kind[\"protocol\"] == ConstantThroughInterface.HTTP).count();", CUSTOM);
        final Endpoint http = custom("svc", 1);
        http.setKind(RequestType.HTTP);
        assertEquals(1, runner.dispatch(http).size());
        final Endpoint mq = custom("svc", 1);
        mq.setKind(RequestType.MQ);
        assertEquals(0, runner.dispatch(mq).size());
    }

    @Test
    public void aSourceWithAMissingOptionalDependencyCompiles() throws Exception {
        // A source and a constants class, each with a member of a type that is not on the classpath, which reflection
        // cannot read past.
        final ClassPool pool = OALScriptRunner.classPool();
        final String optional = Anchor.class.getPackage().getName() + ".";
        pool.makeClass(optional + "MissingIntegration");
        final CtClass source = pool.makeClass(optional + "Endpoint", pool.get(Endpoint.class.getName()));
        source.addMethod(CtNewMethod.make(
            "public " + optional + "MissingIntegration getIntegration() { return null; }", source));
        final Class<?> sourceClass = source.toClass(Anchor.class);
        final CtClass codes = pool.makeClass(optional + "OptionalCodes");
        codes.addField(CtField.make("public static final String OK = \"api\";", codes));
        codes.addField(CtField.make("public static " + optional + "MissingIntegration integration;", codes));
        codes.toClass(Anchor.class);
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_optional = from(Endpoint.*).filter(name == \"api\").count();\n"
                + "chk_optional_const = from(Endpoint.*).filter(name == OptionalCodes.OK).count();", optional);
        final Endpoint api = fill((Endpoint) sourceClass.getDeclaredConstructor().newInstance(), "api", 1);
        assertEquals(2, runner.dispatch(api).size());
        final Endpoint other = fill((Endpoint) sourceClass.getDeclaredConstructor().newInstance(), "web", 1);
        assertEquals(0, runner.dispatch(other).size());
    }

    @Test
    public void aConstantIsLookedUpWithoutInitializingItsClass() throws Exception {
        // ConfiguredLayers initializes only once the layers are ready, as a constant read from Layer.values() can only
        // once the OAP seals its layer registry, after the scripts compile.
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_configured_layer = from(Endpoint.*).filter(serviceLayer == ConfiguredLayers.LOCAL).count();", CUSTOM);
        LayersReady.READY = true;
        final Endpoint mesh = custom("svc", 1);
        mesh.setServiceLayer(Layer.MESH);
        assertEquals(1, runner.dispatch(mesh).size());
        final Endpoint general = custom("svc", 1);
        general.setServiceLayer(Layer.GENERAL);
        assertEquals(0, runner.dispatch(general).size());
    }

    @Test
    public void aConstantIsTheFieldJavassistReads() throws Exception {
        // ConstantThroughInterface.HTTP is its interface's RequestType, found before the superclass's private String.
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_interface_http = from(Endpoint.*).filter(type == ConstantThroughInterface.HTTP).count();", CUSTOM);
        final Endpoint http = custom("svc", 1);
        http.setType(RequestType.HTTP);
        assertEquals(1, runner.dispatch(http).size());
        final Endpoint mq = custom("svc", 1);
        mq.setType(RequestType.MQ);
        assertEquals(0, runner.dispatch(mq).size());
    }

    @Test
    public void anEntranceOfAPackagePrivateTypeIsCalledWithoutACast() throws Exception {
        // ChecksMeasuredMetrics takes a package-private interface, which a cast in the dispatcher could not name.
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_measured = from(Endpoint.measurement).chkMeasured();", CUSTOM);
        final Endpoint endpoint = custom("svc", 1);
        endpoint.setMeasurement(new ChecksMeasurement(5));
        assertEquals(5L, ((ChecksMeasuredMetrics) runner.dispatch(endpoint).get(0)).getTotal());
        // A value the entrance does not take fails the compile, rather than its cast each event.
        assertCompileFails("chk_whole_name = from(Service.name).chkWholeSource();");
    }

    @Test
    public void aLongAttributePassesToAnIntParameterThroughACast() throws Exception {
        // ChecksIntArgMetrics declares an int @Arg; a long attribute passes through (int).
        assertNotNull(OALScriptRunner.enrich("chk_int_arg_long = from(ServiceInstanceJVMMemory.*).chkIntArg(max);"));
        assertNotNull(OALScriptRunner.enrich("chk_int_arg_int = from(Service.*).chkIntArg(latency);"));
        // Shipped scripts feed long millisecond latencies to percentile2() and apdex().
        assertNotNull(OALScriptRunner.enrich("chk_mq_p2 = from(MQAccess.transmissionLatency).percentile2(10);"));
    }

    @Test
    public void aBoxedAttributeFailsTheCompile() {
        // Javassist does not unbox: (long)(Integer) fails the verifier, which the compiler runs on the class it loads.
        final OALCompileException e = assertThrows(OALCompileException.class, () -> OALScriptRunner.compile(
            "chk_boxed = from(Endpoint.queueDepth).longAvg();", CUSTOM));
        assertTrue(e.getMessage().startsWith("checks.oal:1:0 chk_boxed: the generated "), e.getMessage());
    }

    // The from() attribute

    @Test
    public void anAttributeNoFunctionReadsStillCountsEveryEvent() throws Exception {
        // It cannot change the result, so it compiles, with a warning in the log.
        final Map<String, Metrics> metrics = byClass(OALScriptRunner.compile(
            "chk_cpm_attr = from(Service.latency).cpm();\n"
                + "chk_count_cast = from((str->long)Service.name).count();\n"
                + "chk_percent_named = from(Service.status).percent(status == true);").dispatch(service("svc", 1)));
        assertEquals(1L, ((CountMetrics) metrics.get("ChkCountCastMetrics")).getValue());
        assertTrue(metrics.containsKey("ChkCpmAttrMetrics"));
        assertTrue(metrics.containsKey("ChkPercentNamedMetrics"));
    }

    @Test
    public void aFunctionReadingTheSourceNeedsAnAttributeOrTakesTheSource() throws Exception {
        // The JVM's verifier rejects the source passed for a number; the compiler links the class.
        assertCompileFails("chk_avg_wild = from(Service.*).longAvg();");
        // A function whose @SourceFrom parameter takes the source class gets the whole source.
        final List<Metrics> metrics = OALScriptRunner.compile("chk_whole = from(Service.*).chkWholeSource();")
                                                      .dispatch(service("whole", 1));
        assertEquals("whole", ((ChecksWholeSourceMetrics) metrics.get(0)).getSourceName());
        assertCompileFails("chk_whole_cast = from((str->long)Service.*).chkWholeSource();");
    }

    @Test
    public void theWildcardIsNotCombinedWithAnAttribute() {
        final IllegalArgumentException e = assertThrows(
            IllegalArgumentException.class, () -> OALScriptRunner.enrich("chk_wild = from(Service.latency.*).longAvg();"));
        assertTrue(e.getMessage().contains("takes either * or an attribute path, not both"), e.getMessage());
    }

    @Test
    public void aFractionIsNotCutOff() throws Exception {
        assertRejected("chk_avg_double = from(ServiceInstanceJVMCPU.usePercent).longAvg();",
                       "is of type double, but longAvg() takes long: the conversion would lose part of the value");
        assertCompileFails("chk_avg_string = from(Service.name).longAvg();");
        assertCompileFails("chk_avg_cast_int = from((str->long)Service.latency).longAvg();");
        assertCompileFails("chk_avg_unknown = from(Service.nope).longAvg();");
    }

    // Filters

    @Test
    public void numberLiteralsKeepTheirValue() throws Exception {
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_num_sep = from(Service.*).filter(latency > 1_000L).count();");
        assertEquals(0, runner.dispatch(service("svc", 999)).size());
        assertEquals(1, runner.dispatch(service("svc", 1001)).size());
        assertRejected("chk_num_big = from(Service.*).filter(latency > 3000000000).count();",
                       "3000000000 is out of its range");
        assertRejected("chk_num_list_big = from(Service.*).filter(latency in [100, 3000000000]).count();",
                       "3000000000 is out of its range");
        // A number against a string field, or != against a long one, has no matcher overload: Javassist says so.
        assertCompileFails("chk_num_string = from(Service.*).filter(rpcStatusCode == 100L).count();");
        assertCompileFails("chk_num_ne_long = from(ServiceInstanceJVMMemory.*).filter(max != 0).count();");
    }

    @Test
    public void aNumberListMatches() throws Exception {
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_in_numbers = from(Service.*).filter(httpResponseStatusCode in [404, 500]).count();");
        final Service matched = service("svc", 1);
        matched.setHttpResponseStatusCode(500);
        assertEquals(1, runner.dispatch(matched).size());
        final Service other = service("svc", 1);
        other.setHttpResponseStatusCode(200);
        assertEquals(0, runner.dispatch(other).size());
    }

    @Test
    public void aControlCharacterInAStringIsComparedAsWritten() throws Exception {
        // Javassist reads no escape but \n \t \r \f; any other character it takes as written.
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_ctrl_escaped = from(Service.*).filter(name == \"a\\bc\").count();\n"
                + "chk_ctrl_raw = from(Service.*).filter(name == \"a\u0001c\").count();");
        assertEquals(List.of("ChkCtrlEscapedMetrics"), sorted(byClass(runner.dispatch(service("a\bc", 1)))));
        assertEquals(List.of("ChkCtrlRawMetrics"), sorted(byClass(runner.dispatch(service("a\u0001c", 1)))));
        assertEquals(List.of(), sorted(byClass(runner.dispatch(service("abc", 1)))));
    }

    @Test
    public void aQuotedStringWithADotStaysAString() throws Exception {
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_in_dotted = from(Service.*).filter(name in [\"v1.0\", \"RequestType.MQ\"]).count();");
        assertEquals(1, runner.dispatch(service("v1.0", 1)).size());
        assertEquals(1, runner.dispatch(service("RequestType.MQ", 1)).size());
        assertEquals(0, runner.dispatch(service("v2", 1)).size());
    }

    @Test
    public void stringEscapesAreDecoded() throws Exception {
        final String cafe = "caf\u00e9";
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_esc_eq = from(Service.*).filter(name == \"caf\\u00e9\").count();\n"
                + "chk_esc_in = from(Service.*).filter(name in [\"\\101\", \"a\\\\b\\\"c\"]).count();\n"
                + "chk_esc_key = from(Service.*).filter(tag[\"caf\\u00e9\"] == \"x\").count();");
        final Service named = service(cafe, 1);
        named.setOriginalTags(Collections.singletonMap(cafe, "x"));
        final Map<String, Metrics> first = byClass(runner.dispatch(named));
        assertTrue(first.containsKey("ChkEscEqMetrics"));
        assertFalse(first.containsKey("ChkEscInMetrics"));
        assertTrue(first.containsKey("ChkEscKeyMetrics"));
        assertFalse(byClass(runner.dispatch(service("cafu00e9", 1))).containsKey("ChkEscEqMetrics"));
        assertTrue(byClass(runner.dispatch(service("A", 1))).containsKey("ChkEscInMetrics"));
        assertTrue(byClass(runner.dispatch(service("a\\b\"c", 1))).containsKey("ChkEscInMetrics"));
    }

    @Test
    public void stringEqualityComparesAsWritten() throws Exception {
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_q_eq = from(Service.*).filter(tag[\"etag\"] == \"v1\").count();\n"
                + "chk_q_ne = from(Service.*).filter(tag[\"etag\"] != \"v1\").count();\n"
                + "chk_q_quoted_eq = from(Service.*).filter(tag[\"etag\"] == \"\\\"v1\\\"\").count();\n"
                + "chk_q_quoted_in = from(Service.*).filter(tag[\"etag\"] in [\"\\\"v1\\\"\"]).count();\n"
                + "chk_q_quoted_contain = from(Service.*).filter(tags contain \"\\\"v1\\\"\").count();\n"
                + "chk_q_bare_in = from(Service.*).filter(tag[\"etag\"] in [\"v1\"]).count();");
        final Map<String, Metrics> quoted = byClass(runner.dispatch(etag("\"v1\"")));
        assertEquals(List.of("ChkQNeMetrics", "ChkQQuotedContainMetrics", "ChkQQuotedEqMetrics", "ChkQQuotedInMetrics"),
                     sorted(quoted));
        final Map<String, Metrics> bare = byClass(runner.dispatch(etag("v1")));
        assertEquals(List.of("ChkQBareInMetrics", "ChkQEqMetrics"), sorted(bare));
        // A lone quote compares as written, where StringMatch would throw and skip the source's later metrics.
        final Map<String, Metrics> loneQuote = byClass(runner.dispatch(etag("\"")));
        assertEquals(List.of("ChkQNeMetrics"), sorted(loneQuote));
    }

    private static Service etag(String value) {
        final Service service = service("svc", 1);
        service.setOriginalTags(Collections.singletonMap("etag", value));
        service.getTags().add(value);
        return service;
    }

    private static List<String> sorted(Map<String, Metrics> metrics) {
        final List<String> names = new ArrayList<>(metrics.keySet());
        Collections.sort(names);
        return names;
    }

    @Test
    public void aValueOfAnotherTypeIsRejected() {
        // Certain from the declared types to never match: an enum and a string.
        assertRejected("chk_enum_str = from(Service.*).filter(type == \"MQ\").count();",
                       "Service.type is of type RequestType, which never equals a string");
        assertRejected("chk_enum_ne_str = from(Service.*).filter(type != \"MQ\").count();",
                       "Service.type is of type RequestType, which never equals a string");
        assertRejected("chk_enum_in_str = from(Service.*).filter(type in [\"MQ\"]).count();",
                       "which never equals the string \"MQ\"");
        assertRejected("chk_percent_enum = from(Service.*).percent(type == \"MQ\");",
                       "Service.type is of type RequestType, which never equals a string");
        // Javassist fails the compile.
        assertCompileFails("chk_enum_missing = from(Service.*).filter(type == RequestType.NOPE).count();");
        assertCompileFails("chk_const_unknown = from(Service.*).filter(name == NoSuchType.X).count();");
        assertCompileFails("chk_int_const = from(Service.*).filter(latency == DetectPoint.CLIENT).count();");
        assertCompileFails("chk_int_str = from(Service.*).filter(latency == \"1\").count();");
        assertCompileFails("chk_int_bool = from(Service.*).filter(latency == true).count();");
        assertCompileFails("chk_contain_str = from(Service.*).filter(name contain \"a\").count();");
    }

    @Test
    public void aConstantIsOfTheSourcePackage() throws Exception {
        // TYPE.NAME names a class of the source package: core.analysis.Layer is none.
        assertCompileFails("chk_const_layer = from(Service.*).filter(layer == Layer.MESH).count();");

        final OALScriptRunner cilium = OALScriptRunner.compile(
            "chk_const_verdict = from(CiliumService.*).filter(verdict == CiliumMetrics.VERDICT_DROPPED).count();");
        final CiliumService dropped = new CiliumService();
        dropped.setServiceName("svc");
        dropped.setLayer(Layer.CILIUM_SERVICE);
        dropped.setTimeBucket(202611201200L);
        dropped.setVerdict(CiliumMetrics.VERDICT_DROPPED);
        assertEquals(1, cilium.dispatch(dropped).size());
        dropped.setVerdict(CiliumMetrics.VERDICT_FORWARDED);
        assertEquals(0, cilium.dispatch(dropped).size());

        // A number constant takes a constant's matcher: NotEqualMatch has an int overload, StringMatch none.
        final OALScriptRunner ints = OALScriptRunner.compile(
            "chk_const_int_ne = from(Service.*).filter(httpResponseStatusCode != DefaultScopeDefine.SERVICE).count();");
        assertEquals(0, ints.dispatch(statusCode(DefaultScopeDefine.SERVICE)).size());
        assertEquals(1, ints.dispatch(statusCode(404)).size());
        assertCompileFails(
            "chk_const_int_eq = from(Service.*).filter(httpResponseStatusCode == DefaultScopeDefine.SERVICE).count();");
    }

    private static Service statusCode(int code) {
        final Service service = service("svc", 1);
        service.setHttpResponseStatusCode(code);
        return service;
    }

    @Test
    public void byteAndShortCompareAsInt() throws Exception {
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_short_ge = from(Endpoint.*).filter(statusCode >= 200).count();\n"
                + "chk_byte_eq = from(Endpoint.*).filter(retries == 1).count();\n"
                + "chk_short_const = from(Endpoint.*).filter(latency != HttpCodes.SHORT_OK).count();", CUSTOM);
        final Endpoint low = custom("svc", 200);
        low.setStatusCode((short) 199);
        low.setRetries((byte) 1);
        assertEquals(List.of("ChkByteEqMetrics"), sorted(byClass(runner.dispatch(low))));
        final Endpoint ok = custom("svc", 404);
        ok.setStatusCode((short) 200);
        assertEquals(List.of("ChkShortConstMetrics", "ChkShortGeMetrics"), sorted(byClass(runner.dispatch(ok))));
    }

    @Test
    public void aFieldThatMayHoldAStringComparesWithOne() throws Exception {
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_object_eq = from(Endpoint.*).filter(objectTag == \"fast\").count();\n"
                + "chk_object_ne = from(Endpoint.*).filter(objectTag != \"fast\").count();\n"
                + "chk_route_in = from(Endpoint.*).filter(route in [\"fast\"]).count();\n"
                + "chk_raw_contain = from(Endpoint.*).filter(rawTags contain \"fast\").count();", CUSTOM);
        final Endpoint fast = custom("svc", 1);
        fast.setObjectTag("fast");
        fast.setRoute("fast");
        fast.setRawTags(List.of("fast"));
        assertEquals(List.of("ChkObjectEqMetrics", "ChkRawContainMetrics", "ChkRouteInMetrics"),
                     sorted(byClass(runner.dispatch(fast))));
        final Endpoint slow = custom("svc", 1);
        slow.setObjectTag("slow");
        slow.setRoute("slow");
        slow.setRawTags(List.of("slow"));
        assertEquals(List.of("ChkObjectNeMetrics"), sorted(byClass(runner.dispatch(slow))));
    }

    @Test
    public void aListOfConstantsComparesAsWritten() throws Exception {
        final OALScriptRunner strings = OALScriptRunner.compile(
            "chk_const_in_etag = from(Endpoint.*).filter(etag in [HttpCodes.ETAG]).count();\n"
                + "chk_const_in_quote = from(Endpoint.*).filter(etag in [HttpCodes.QUOTE]).count();", CUSTOM);
        final Endpoint quoted = custom("svc", 1);
        quoted.setEtag("\"v1\"");
        assertEquals(List.of("ChkConstInEtagMetrics"), sorted(byClass(strings.dispatch(quoted))));
        final Endpoint bare = custom("svc", 1);
        bare.setEtag("v1");
        assertEquals(List.of(), sorted(byClass(strings.dispatch(bare))));
        final Endpoint quote = custom("svc", 1);
        quote.setEtag("\"");
        assertEquals(List.of("ChkConstInQuoteMetrics"), sorted(byClass(strings.dispatch(quote))));

        // A list of number constants goes in an Object[], which the JVM rejects for an int.
        assertCompileFails("chk_const_in_numbers = from(Service.*).filter(latency in [DefaultScopeDefine.SERVICE, "
                               + "DefaultScopeDefine.ENDPOINT]).count();");
    }

    @Test
    public void aDisabledRuleIsStillDispatched() throws Exception {
        // Its stream does not run, but its decorator still fills attr0 for the rules after it.
        assertDisabledDecoratorFeeds("chk_prime_off", "chk_mesh_calls_off");
        final boolean injecting = DSLDebugCodegenSwitch.isInjectionEnabled();
        DSLDebugCodegenSwitch.enableInjection();
        try {
            assertDisabledDecoratorFeeds("chk_prime_on", "chk_mesh_calls_on");
        } finally {
            if (!injecting) {
                DSLDebugCodegenSwitch.resetInjection();
            }
        }
    }

    private static void assertDisabledDecoratorFeeds(String prime, String meshCalls) throws Exception {
        final OALScriptRunner runner = OALScriptRunner.compile(
            prime + " = from(Service.*).count().decorator(\"ServiceDecorator\");\n"
                + meshCalls + " = from(Service.*).filter(attr0 == \"MESH\").count().decorator(\"ServiceDecorator\");\n"
                + "disable(" + prime + ");");
        final Service mesh = service("svc", 1);
        mesh.setLayer(Layer.MESH);
        assertTrue(byClass(runner.dispatch(mesh)).containsKey(metricsClassName(meshCalls)));
    }

    private static String metricsClassName(String metric) {
        final StringBuilder name = new StringBuilder();
        for (String part : metric.split("_")) {
            name.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return name.append("Metrics").toString();
    }

    @Test
    public void aConstantOfAHiddenClass() throws Exception {
        // A compile-time constant is copied into the generated code.
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_hidden_inlined = from(Endpoint.*).filter(name == HiddenCodes.INLINED).count();\n"
                + "chk_hidden_number = from(Endpoint.*).filter(latency != HiddenCodes.OK).count();", CUSTOM);
        assertEquals(List.of("ChkHiddenInlinedMetrics"), sorted(byClass(runner.dispatch(custom("GET", 200)))));
        assertEquals(List.of("ChkHiddenNumberMetrics"), sorted(byClass(runner.dispatch(custom("PUT", 1)))));
        // Any other read of it fails on the first event: loud, so the compile leaves it.
        final OALScriptRunner unreadable = OALScriptRunner.compile(
            "chk_hidden = from(Endpoint.*).filter(name in [HiddenCodes.VALUE]).count();", CUSTOM);
        assertThrows(IllegalAccessError.class, () -> unreadable.dispatch(custom("GET", 1)));
    }

    @Test
    public void conversionsThatKeepTheValueCompile() throws Exception {
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_array_sum = from(Endpoint.samples).chkArraySum();\n"
                + "chk_char_avg = from(Endpoint.result).longAvg();\n"
                + "chk_bool_label = from(Endpoint.*).labelAvg(name, feature);\n"
                + "chk_bridge = from(Endpoint.*).filter(headerValue[\"method\"] == \"GET\").count();\n"
                + "chk_serial_eq = from(Endpoint.*).filter(route == HttpCodes.SERIAL).count();\n"
                + "chk_serial_ne = from(Endpoint.*).filter(route != HttpCodes.SERIAL).count();\n"
                + "chk_serial_in = from(Endpoint.*).filter(route in [HttpCodes.SERIAL]).count();\n"
                + "chk_indexed = from(Endpoint.*).filter(indexTags contain \"PUT\").count();", CUSTOM);
        final Endpoint get = custom("svc", 1);
        get.setSamples(new long[] {12, 25});
        get.setResult('g');
        get.setFeature(true);
        get.setHeaders(Collections.singletonMap("method", "GET"));
        get.setRoute("GET");
        get.setIndexTags(new IndexedTags<>());
        final Map<String, Metrics> first = byClass(runner.dispatch(get));
        assertEquals(List.of("ChkArraySumMetrics", "ChkBoolLabelMetrics", "ChkBridgeMetrics", "ChkCharAvgMetrics",
                             "ChkSerialEqMetrics", "ChkSerialInMetrics"), sorted(first));
        assertEquals(37L, ((ChecksArrayMetrics) first.get("ChkArraySumMetrics")).getTotal());
        assertEquals(103L, field(first.get("ChkCharAvgMetrics"), "summation"));
        assertEquals(1L, ((LabelAvgMetrics) first.get("ChkBoolLabelMetrics")).getSummation().get("svc"));
        final Endpoint put = custom("svc", 1);
        put.setSamples(new long[0]);
        put.setHeaders(Collections.singletonMap("method", "PUT"));
        put.setRoute("PATCH");
        final IndexedTags<Integer> tags = new IndexedTags<>();
        tags.add("PUT");
        put.setIndexTags(tags);
        final Map<String, Metrics> second = byClass(runner.dispatch(put));
        assertEquals(List.of("ChkArraySumMetrics", "ChkBoolLabelMetrics", "ChkCharAvgMetrics", "ChkIndexedMetrics",
                             "ChkSerialNeMetrics"), sorted(second));
        assertEquals(0L, ((LabelAvgMetrics) second.get("ChkBoolLabelMetrics")).getSummation().get("svc"));
    }

    @Test
    public void customAttributeAndConstantFormsMatch() throws Exception {
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_keyed_label = from(Endpoint.*).labelCount(origin.label[\"region\"]);\n"
                + "chk_keyed_overload = from(Endpoint.*).filter(header[\"method\"] == \"GET\").count();\n"
                + "chk_cs_eq = from(Endpoint.*).filter(etag == HttpCodes.GET).count();\n"
                + "chk_cs_ne = from(Endpoint.*).filter(etag != HttpCodes.GET).count();\n"
                + "chk_cs_in = from(Endpoint.*).filter(etag in [HttpCodes.GET]).count();\n"
                + "chk_char_ne = from(Endpoint.*).filter(result != HttpCodes.GOOD).count();\n"
                + "chk_bool_sum = from(Endpoint.feature).sum();", CUSTOM);
        final Endpoint get = custom("svc", 1);
        get.setOrigin(new Origin(Collections.singletonMap("region", "us-east")));
        get.setHeaders(Collections.singletonMap("method", "GET"));
        get.setEtag("GET");
        get.setResult('g');
        get.setFeature(true);
        final Map<String, Metrics> first = byClass(runner.dispatch(get));
        assertEquals(List.of("ChkBoolSumMetrics", "ChkCsEqMetrics", "ChkCsInMetrics", "ChkKeyedLabelMetrics",
                             "ChkKeyedOverloadMetrics"), sorted(first));
        assertEquals(1L, ((LabelCountMetrics) first.get("ChkKeyedLabelMetrics")).getDataset().get("us-east"));
        assertEquals(1L, field(first.get("ChkBoolSumMetrics"), "value"));
        final Endpoint put = custom("svc", 1);
        put.setOrigin(new Origin(Collections.singletonMap("region", "eu-west")));
        put.setHeaders(Collections.singletonMap("method", "PUT"));
        put.setEtag("PUT");
        put.setResult('b');
        final Map<String, Metrics> second = byClass(runner.dispatch(put));
        assertEquals(List.of("ChkBoolSumMetrics", "ChkCharNeMetrics", "ChkCsNeMetrics", "ChkKeyedLabelMetrics"),
                     sorted(second));
        assertEquals(0L, field(second.get("ChkBoolSumMetrics"), "value"));
    }

    @Test
    public void constantsAndKeyedGettersResolveAsJavassistResolvesThem() throws Exception {
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_alias_layer = from(Endpoint.*).filter(serviceLayer == Layer.GENERAL).count();\n"
                + "chk_list_eq = from(Endpoint.*).filter(path == HttpCodes.PATH).count();\n"
                + "chk_list_in = from(Endpoint.*).filter(path in [HttpCodes.PATH]).count();\n"
                + "chk_header_fallback = from(Endpoint.*).filter(header[\"method\"] == \"GET\").count();\n"
                + "chk_private_int = from(Endpoint.*).filter(httpResponseStatusCode != HttpCodes.PRIVATE_OK).count();\n"
                + "chk_private_str = from(Endpoint.*).filter(name == HttpCodes.PRIVATE_GET).count();", CUSTOM);
        // The source package's Layer, an alias of MESH: TYPE.NAME names a class of the source package only.
        final Endpoint mesh = custom("GET", 1);
        mesh.setServiceLayer(Layer.MESH);
        mesh.setPath(new ArrayList<>(List.of("api", "v1")));
        mesh.setHeaders(Collections.singletonMap("method", "GET"));
        mesh.setHttpResponseStatusCode(500);
        assertEquals(Set.of("ChkAliasLayerMetrics", "ChkHeaderFallbackMetrics", "ChkListEqMetrics", "ChkListInMetrics",
                            "ChkPrivateIntMetrics", "ChkPrivateStrMetrics"), byClass(runner.dispatch(mesh)).keySet());
        final Endpoint other = custom("POST", 1);
        other.setServiceLayer(Layer.GENERAL);
        other.setPath(new ArrayList<>(List.of("api", "v2")));
        other.setHeaders(Collections.singletonMap("method", "POST"));
        other.setHttpResponseStatusCode(200);
        assertEquals(Set.of(), byClass(runner.dispatch(other)).keySet());
    }

    @Test
    public void aSourceDecoratedThroughAWiderParameterIsDecorated() throws Exception {
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_deco_wider = from(MQAccess.*).cpm().decorator(\"ServiceDecorator\");", CUSTOM);
        final MQAccess access = new MQAccess();
        access.setName("queue");
        access.setTimeBucket(202611201200L);
        assertEquals(1, runner.dispatch(access).size());
        assertEquals(1, access.getDecorated());
    }

    @Test
    public void noGeneratedConstructorRunsBeforeTheStreamIsRegistered() throws Exception {
        // Its constructor reads @Stream, which names the builder generated after the metrics class.
        final OALScriptRunner runner = OALScriptRunner.compile("chk_stream_name = from(Service.*).chkStreamName();");
        final Class<?> metricsClass = runner.metricsClasses().get(0);
        assertNotNull(metricsClass.getAnnotation(Stream.class).builder());
        assertEquals("chk_stream_name",
                     ((ChecksStreamNameMetrics) metricsClass.getDeclaredConstructor().newInstance()).getStreamName());
    }

    @Test
    public void aRegisteredMatcherGetsTheValueAsWritten() throws Exception {
        // A matcher registered under an operator's name replaces the stock one, and gets the value as written: no exact
        // list, no added quotes, no pattern or type check.
        final OALScriptRunner in = compileWith("inMatch", ChecksCustomMatchers.CaseInsensitiveIn.class,
            "chk_custom_in = from(Service.*).filter(tag[\"http.method\"] in [\"get\", \"post\"]).count();");
        final Service get = service("svc", 1);
        get.setOriginalTags(Collections.singletonMap("http.method", "GET"));
        assertEquals(1, in.dispatch(get).size());
        final OALScriptRunner contain = compileWith("containMatch", ChecksCustomMatchers.ExactContain.class,
            "chk_custom_contain = from(Service.*).filter(tags contain \"\\\"v1\\\"\").count();");
        final Service quoted = service("svc", 1);
        quoted.setTags(new ArrayList<>(List.of("\"v1\"")));
        assertEquals(1, contain.dispatch(quoted).size());
        final OALScriptRunner like = compileWith("likeMatch", ChecksCustomMatchers.ExactLike.class,
            "chk_custom_like = from(Service.*).filter(name like \"svc\").count();");
        assertEquals(1, like.dispatch(service("svc", 1)).size());
        assertEquals(0, like.dispatch(service("svc2", 1)).size());
        final OALScriptRunner enumName = compileWith("stringMatch", ChecksCustomMatchers.EnumName.class,
            "chk_custom_enum = from(Service.*).filter(type == \"HTTP\").count();");
        final Service http = service("svc", 1);
        http.setType(RequestType.HTTP);
        assertEquals(1, enumName.dispatch(http).size());
        // the list items and the number spellings as written: a dotted string names a constant, a number with an L is
        // its spelling, of a string's kind
        final OALScriptRunner dotted = compileWith("inMatch", ChecksCustomMatchers.CaseInsensitiveIn.class,
            "chk_custom_dotted = from(Service.*).filter(type in [\"RequestType.HTTP\"]).count();");
        assertEquals(1, dotted.dispatch(http).size());
        final OALScriptRunner spelled = compileWith("stringMatch", ChecksCustomMatchers.EnumName.class,
            "chk_custom_spelled = from(Service.*).filter(name == 100L).count();");
        assertEquals(1, spelled.dispatch(service("100L", 1)).size());
        // a number the stock matchers refuse, and a string's escapes, reach it as written
        final OALScriptRunner octal = compileWith("stringMatch", ChecksCustomMatchers.EnumName.class,
            "chk_custom_octal = from(Service.*).filter(name == 010L).count();");
        assertEquals(1, octal.dispatch(service("010L", 1)).size());
        final OALScriptRunner escaped = compileWith("stringMatch", ChecksCustomMatchers.EnumName.class,
            "chk_custom_escaped = from(Service.*).filter(name == \"a\\tb\").count();");
        assertEquals(1, escaped.dispatch(service("a\tb", 1)).size());
        // a value whose kind as read chooses a registered matcher the kind as written does not: the call of the value
        // as written
        final OALScriptRunner writtenKind = compileWith("numberMatch", ChecksCustomMatchers.CaseInsensitiveIn.class,
            "chk_custom_written_kind = from(Service.*).filter(name == 100L).count();");
        assertEquals(1, writtenKind.dispatch(service("100L", 1)).size());
        // the stock exact membership is bound itself, never one registered under its name
        final OALScriptRunner exact = compileWith("exactInMatch", ChecksCustomMatchers.CaseInsensitiveIn.class,
            "chk_custom_exact = from(Service.*).filter(name in [\"get\"]).count();");
        assertEquals(0, exact.dispatch(service("GET", 1)).size());
        // a value whose kind as written chooses no matcher reaches no registered one
        assertThrows(Exception.class, () -> compileWith("greaterMatch", ChecksCustomMatchers.CaseInsensitiveIn.class,
            "chk_custom_refused = from(Service.*).filter(latency > 100L).count();"));
        // a cast is refused whichever matcher takes the value
        assertThrows(Exception.class, () -> compileWith("inMatch",
            ChecksCustomMatchers.CaseInsensitiveIn.class,
            "chk_custom_cast = from(Service.*).filter((str->long)name in [\"svc\"]).count();"));
    }

    private static OALScriptRunner compileWith(String type, Class<?> matcher, String script) throws Exception {
        final Field field = FilterMatchers.class.getDeclaredField("matchersKeyedByType");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        final Map<String, FilterMatchers.MatcherInfo> matchers =
            (Map<String, FilterMatchers.MatcherInfo>) field.get(FilterMatchers.INSTANCE);
        final FilterMatchers.MatcherInfo stock = matchers.put(type, new FilterMatchers.MatcherInfo(matcher, false));
        try {
            return OALScriptRunner.compile(script);
        } finally {
            matchers.put(type, stock);
        }
    }

    @Test
    public void aLikePatternMatchesOrIsRejected() throws Exception {
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_like_prefix = from(Service.*).filter(name like \"ab%\").count();");
        assertEquals(1, runner.dispatch(service("abc", 1)).size());
        assertEquals(0, runner.dispatch(service("xab", 1)).size());
        assertRejected("chk_like_exact = from(Service.*).filter(name like \"abc\").count();",
                       "a like pattern starts or ends with %");
        assertRejected("chk_like_any = from(Service.*).filter(name like \"%\").count();",
                       "and is more than % alone");
        // LikeMatch takes a pair of quotes off the pattern, so a quoted one checks as what it matches with.
        final OALScriptRunner quoted = OALScriptRunner.compile(
            "chk_like_quoted = from(Service.*).filter(name like \"\\\"sv%\\\"\").count();\n"
                + "chk_like_all = from(Service.*).filter(name like \"%%\").count();");
        assertEquals(Set.of("ChkLikeQuotedMetrics", "ChkLikeAllMetrics"),
                     byClass(quoted.dispatch(service("svc", 1))).keySet());
        assertEquals(Set.of("ChkLikeAllMetrics"), byClass(quoted.dispatch(service("abc", 1))).keySet());
    }

    @Test
    public void anEnumConstantMatches() throws Exception {
        final OALScriptRunner runner = OALScriptRunner.compile(
            "chk_enum_in = from(Service.*).filter(type in [RequestType.MQ, RequestType.RPC]).count();");
        final Service mq = service("svc", 1);
        mq.setType(RequestType.MQ);
        final List<Metrics> metrics = runner.dispatch(mq);
        assertEquals(1, metrics.size());
        assertEquals(1L, ((CountMetrics) metrics.get(0)).getValue());
    }

    @Test
    public void theDocumentedExamplesCompile() throws Exception {
        File doc = new File("../../docs/en/concepts-and-designs/oal.md");
        if (!doc.exists()) {
            doc = new File("docs/en/concepts-and-designs/oal.md");
        }
        final String text = new String(Files.readAllBytes(doc.toPath()), StandardCharsets.UTF_8);
        final int start = text.indexOf("```", text.indexOf("## Examples")) + 3;
        // Renamed: the generated classes share a package with core.oal's, compiled in this JVM too.
        final String examples = text.substring(start, text.indexOf("```", start))
                                    .replaceAll("(?m)^([a-zA-Z_]\\w*) = from\\(", "chk_doc_$1 = from(");
        final List<CodeGenModel> models = OALScriptRunner.enrich(examples);
        assertEquals(11, models.size());
        assertEquals(11 + 2, OALScriptRunner.generate(models));

        // The one-line examples of the functions, but for the browser scopes, which use another source package.
        final StringBuilder functions = new StringBuilder();
        for (String line : text.split("\n")) {
            if (line.startsWith("> ") && line.contains(" = from(") && !line.contains("from(Browser")) {
                functions.append("chk_fn_").append(line.substring(2)).append('\n');
            }
        }
        final List<CodeGenModel> functionModels = OALScriptRunner.enrich(functions.toString());
        assertEquals(10, functionModels.size());
        assertTrue(OALScriptRunner.generate(functionModels) > 10);
    }

    // Decorators

    @Test
    public void aDecoratorMayFeedTheLabelOfALabeledMetric() throws Exception {
        // The dispatcher decorates the source before labelCount reads attr0.
        final Service mesh = service("svc", 1);
        mesh.setLayer(Layer.MESH);
        final List<Metrics> metrics = OALScriptRunner.compile(
            "chk_deco_label = from(Service.*).labelCount(attr0).decorator(\"ServiceDecorator\");").dispatch(mesh);
        assertEquals(1L, ((LabelCountMetrics) metrics.get(0)).getDataset().get("MESH"));
    }

    @Test
    public void aKnownDecoratorRuns() throws Exception {
        final List<Metrics> metrics = OALScriptRunner.compile(
            "chk_deco_known = from(Service.latency).longAvg().decorator(\"ServiceDecorator\");")
                                                      .dispatch(service("svc", 5));
        assertEquals(Layer.GENERAL.name(), field(metrics.get(0), "attr0"));
    }

    // Disable

    @Test
    public void aDisableAfterItsStreamIsDefinedHasNoEffect() throws Exception {
        // As a disable in browser.oal of browser_error_log, which core defines before the browser module starts, or
        // of profile_task, whose cache refresh reads the register. The log says it has no effect, and it has none.
        DisableRegister.INSTANCE.markStreamDefined("chk_running_stream");
        OALScriptRunner.compileDisables("disable(chk_running_stream);");
        assertTrue(DisableRegister.INSTANCE.isStreamRunning("chk_running_stream"));
        assertFalse(DisableRegister.INSTANCE.include("chk_running_stream"));
        assertFalse(DisableRegister.INSTANCE.scriptDisablesWithoutStream().contains("chk_running_stream"));

        // As disable.oal, loaded before any stream.
        OALScriptRunner.compileDisables("disable(chk_later_stream);");
        DisableRegister.INSTANCE.markStreamDefined("chk_later_stream");
        assertTrue(DisableRegister.INSTANCE.include("chk_later_stream"));
        assertFalse(DisableRegister.INSTANCE.scriptDisablesWithoutStream().contains("chk_later_stream"));

        // Disabled already, the stream never ran, so repeating its disable later, as an override may, is harmless.
        OALScriptRunner.compileDisables("disable(chk_later_stream);");
        assertFalse(DisableRegister.INSTANCE.isStreamRunning("chk_later_stream"));
    }

    // Generated classes

    @Test
    public void aGeneratedClassTheVerifierRejectsFailsTheCompile() throws Exception {
        final List<CodeGenModel> models = OALScriptRunner.enrich("chk_verify = from(Service.latency).longAvg();");
        // Javassist compiles a cast of the source object to long; only the JVM's verifier rejects it.
        models.get(0).getEntranceMethod().getArgsExpressions().set(0, "(long)(source)");
        final OALCompileException e = assertThrows(
            OALCompileException.class, () -> OALScriptRunner.compile(models));
        assertTrue(e.getMessage().startsWith("checks.oal:1:0 chk_verify: the generated "), e.getMessage());
        assertTrue(e.getCause() instanceof LinkageError, String.valueOf(e.getCause()));
    }
}
