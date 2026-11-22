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
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import javassist.ClassPool;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.DSL;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.FilterExpression;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalExpression;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.MalFilter;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.Sample;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamily;
import org.apache.skywalking.oap.meter.analyzer.v2.dsl.SampleFamilyBuilder;
import org.apache.skywalking.oap.meter.analyzer.v2.compiler.rt.MalRuntimeHelper;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.junit.jupiter.api.BeforeEach;
import org.apache.skywalking.oap.server.core.dsl.DslSourceRef;
import org.junit.jupiter.api.Test;

import static org.apache.skywalking.oap.meter.analyzer.v2.compiler.MALCompileAssertions.rejects;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a compiled closure does when it runs, as Groovy gives each form its meaning.
 */
class MALClosureSemanticsTest {

    private MALClassGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new MALClassGenerator(new ClassPool(true));
    }

    @Test
    void instancePropertiesAreTheMapTheClosureEndsWith() throws Exception {
        // otel-rules/k8s/k8s-instance.yaml
        final Function<Map<String, String>, Map<String, String>> k8s = instanceProperties(
            "m.sum(['cluster','service','pod','namespace']).instance(['cluster', 'service'], '::', ['pod'], '', "
                + "Layer.K8S_SERVICE, { tags -> ['pod': tags.pod, 'namespace': tags.namespace] })");
        assertEquals(Map.of("pod", "p1", "namespace", "ns"),
            k8s.apply(labels("pod", "p1", "namespace", "ns", "cluster", "c", "service", "s", "secret", "x")));

        // otel-rules/banyandb/banyandb-instance.yaml
        final Function<Map<String, String>, Map<String, String>> banyandb = instanceProperties(
            "m.sum(['cluster','container_name','pod_name']).instance(['cluster'], '::', ['container_name', "
                + "'pod_name'], '@', Layer.BANYANDB, { tags -> ['node_role': tags.node_role, 'node_type': "
                + "tags.node_type ?: 'n/a', 'pod_name': tags.pod_name, 'container_name': tags.container_name] })");
        final Map<String, String> properties =
            banyandb.apply(labels("node_role", "data", "pod_name", "p", "container_name", "c", "cluster", "x"));
        assertEquals("n/a", properties.get("node_type"));
        assertEquals("data", properties.get("node_role"));
        assertFalse(properties.containsKey("cluster"));
    }

    @Test
    void aTagClosureEndingWithAMapReplacesTheLabels() throws Exception {
        assertEquals(Map.of("only", "a1"),
            tagged("m.tag({ tags -> ['only': tags.a] })", labels("a", "a1", "b", "b1")));
    }

    @Test
    void aConditionStatementRunsForItsSideEffects() throws Exception {
        assertEquals(Map.of("svc", "s"),
            tagged("m.tag({ tags -> !tags.remove('a') })", labels("a", "x", "svc", "s")));
        // || short-circuits on a truthy left side, as in Groovy.
        assertEquals(Map.of("svc", "s"),
            tagged("m.tag({ tags -> tags.remove('a') || tags.remove('svc') })", labels("a", "x", "svc", "s")));
    }

    @Test
    void aTernaryComparesStringsByValue() throws Exception {
        // new String: not the interned literal, so reference equality would differ
        assertEquals("diff", tagged("m.tag({ tags -> tags.r = tags.a != 'x' ? 'diff' : 'same' })",
            labels("a", new String("y"))).get("r"));
        assertEquals("eq", tagged("m.tag({ tags -> tags.r = tags.a == 'y' ? 'eq' : 'ne' })",
            labels("a", new String("y"))).get("r"));
    }

    @Test
    void aTernaryTestsGroovyTruth() throws Exception {
        assertEquals("no", tagged("m.tag({ tags -> tags.r = tags.a ? 'yes' : 'no' })", labels("a", "")).get("r"));
        assertEquals("yes", tagged("m.tag({ tags -> tags.r = tags.a ? 'yes' : 'no' })", labels("a", "v")).get("r"));
    }

    @Test
    void closureArithmeticAssociatesLeft() throws Exception {
        assertEquals("v11", tagged("m.tag({ tags -> tags.r = 'v' + (10 - 2 + 3) })", labels()).get("r"));
        assertEquals("v10", tagged("m.tag({ tags -> tags.r = 'v' + (2 * 3 + 4) })", labels()).get("r"));
    }

    @Test
    void relationalBindsTighterThanEquality() throws Exception {
        final MalFilter relational = generator.compileFilter(
            "{ t -> t.failed.equals('true') == Integer.parseInt(t.status) >= 500 }");
        assertTrue(relational.test(Map.of("failed", "true", "status", "503")));
        assertThrows(IllegalArgumentException.class, () -> generator.compileFilter(
            "{ t -> false != t.path =~ /health/ }"), "a regex match beside a comparison must be parenthesized");
    }

    @Test
    void aTernaryComparesNumbersAndNestsToTheRight() throws Exception {
        assertEquals("yes", tagged("m.tag({ tags -> tags.r = tags.a.length() == 2 ? 'yes' : 'no' })",
            labels("a", "ab")).get("r"));
        assertEquals("A", tagged("m.tag({ t -> t.r = t.a ? 'A' : t.b ? 'B' : 'C' })",
            labels("a", "x", "b", "y")).get("r"));
    }

    @Test
    void anInStatementToleratesAMissingLabel() throws Exception {
        assertEquals(Map.of("svc", "s"), tagged("m.tag({ t -> t.missing in ['x'] })", labels("svc", "s")));
    }

    @Test
    void aParameterNamedLikeTheGeneratedMapIsRead() throws Exception {
        assertEquals(Map.of("only", "a1"), tagged("m.tag({ _result -> ['only': _result.a] })", labels("a", "a1")));
    }

    @Test
    void aMapHeldByAVariableIsReturned() throws Exception {
        assertEquals(Map.of("only", "a1"),
            tagged("m.tag({ t -> def picked = ['only': t.a]; picked })", labels("a", "a1", "b", "b1")));
    }

    @Test
    void aFilterCanEndWithAComparison() throws Exception {
        final MalFilter filter = generator.compileFilter("{ t -> String j = t.job; j == 'ok' }");
        assertTrue(filter.test(Map.of("job", "ok")));
        assertFalse(filter.test(Map.of("job", "no")));
    }

    @Test
    void anInterpolatedStringIsAString() throws Exception {
        assertEquals("23", tagged("m.tag({ t -> t.r = \"${t.a.length()}${t.b.length()}\" })",
            labels("a", "ab", "b", "abc")).get("r"));
        assertEquals("null", tagged("m.tag({ t -> t.r = \"${t.missing}\" })", labels()).get("r"));
        assertEquals("${t.a}", tagged("m.tag({ t -> t.r = \"\\${t.a}\" })", labels("a", "x")).get("r"),
            "an escaped $ is text, as in Groovy");
        // \$ before what does not interpolate stays as written, a regex's included
        assertEquals("a\\$2-x", tagged("m.tag({ t -> t.r = \"a\\$2-${t.a}\" })", labels("a", "x")).get("r"));
        final MalFilter price = generator.compileFilter("{ t -> t.price.matches('^\\$[0-9]+$') }");
        assertTrue(price.test(Map.of("price", "$42")));
        assertFalse(price.test(Map.of("price", "42")));
    }

    @Test
    void aFilterCanReturnItsCondition() throws Exception {
        final MalFilter filter = generator.compileFilter("{ t -> return t.job == 'ok' }");
        assertTrue(filter.test(Map.of("job", "ok")));
        assertFalse(filter.test(Map.of("job", "no")));
    }

    @Test
    void comparisonBindsTighterThanTheTernaryAndElvisNestsInIt() throws Exception {
        assertEquals("A", tagged("m.tag({ t -> t.r = t.a == 'x' ? 'A' : t.b ? 'B' : 'C' })",
            labels("a", "x", "b", "y")).get("r"));
        assertEquals("", tagged("m.tag({ t -> t.r = t.a ? '' : t.b ?: 'C' })", labels("a", "x", "b", "")).get("r"));
    }

    @Test
    void theValueAClosureEndsWithIsItsResult() throws Exception {
        assertEquals(Map.of("only", "a1"),
            tagged("m.tag({ t -> t.getOrDefault('absent', ['only': t.a]) })", labels("a", "a1", "b", "b1")));
        assertEquals(Map.of("only", "a1"),
            tagged("m.tag({ t -> def picked = ['only': t.a] })", labels("a", "a1", "b", "b1")));
        assertEquals(Map.of("role", "data"), tagged(
            "m.tag({ t -> if (t.role == 'data') { ['role': 'data'] } else { ['role': t.role + '-x'] } })",
            labels("role", "data", "other", "o")));
        assertEquals(Map.of("role", "r-x"), tagged(
            "m.tag({ t -> if (t.role == 'data') { ['role': 'data'] } else { ['role': t.role + '-x'] } })",
            labels("role", "r", "other", "o")));
        final Function<Map<String, String>, Map<String, String>> properties = instanceProperties(
            "m.sum(['a', 'b']).instance(['a'], '', ['b'], '', Layer.GENERAL, { t -> t.a ? ['only': t.a] : null })");
        assertEquals(null, properties.apply(labels("b", "b1")), "no properties");
        assertEquals(Map.of("only", "a1"), properties.apply(labels("a", "a1", "b", "b1")));
    }

    @Test
    void aTrailingAssignmentIsTheClosuresValue() throws Exception {
        assertEquals(Map.of("id", "i", "r", "v"), tagged("m.tag({ t -> t.r = 'v' })", labels("id", "i")));
    }

    @Test
    void filterBodiesEndWithTheirCondition() throws Exception {
        final MalFilter branches = generator.compileFilter(
            "{ t -> if (t.job) { return true } else { return false } }");
        assertTrue(branches.test(Map.of("job", "x")));
        assertFalse(branches.test(Map.of()));
        final SampleFamily filtered = generator.compile("t", "m.filter({ t -> t.job == 'ok' })").run(Map.of("m",
            SampleFamilyBuilder.newBuilder(
                Sample.builder().name("m").labels(ImmutableMap.of("job", "ok")).value(1.0).build(),
                Sample.builder().name("m").labels(ImmutableMap.of("job", "no")).value(2.0).build()).build()));
        assertEquals(1, filtered.samples.length);
        final String ifElse = "{ t -> if (t.env == 'prod') { t.job == 'api' } else { t.job == 'batch' } }";
        final MalFilter yaml = generator.compileFilter(ifElse);
        assertTrue(yaml.test(Map.of("env", "prod", "job", "api")));
        assertFalse(yaml.test(Map.of("env", "prod", "job", "batch")));
        assertTrue(yaml.test(Map.of("env", "dev", "job", "batch")));
        final SampleFamily chained = generator.compile("t", "m.filter(" + ifElse + ")").run(Map.of("m",
            SampleFamilyBuilder.newBuilder(
                Sample.builder().name("m").labels(ImmutableMap.of("env", "prod", "job", "api")).value(1.0).build(),
                Sample.builder().name("m").labels(ImmutableMap.of("env", "prod", "job", "x")).value(2.0).build())
                .build()));
        assertEquals(1, chained.samples.length);
        final MalFilter keep = generator.compileFilter(
            "{ t -> boolean keep = false\n if (t.env == 'prod') { keep = t.job == 'api' } else { false } }");
        assertTrue(keep.test(Map.of("env", "prod", "job", "api")));
        assertFalse(keep.test(Map.of("env", "prod", "job", "x")));
        final MalFilter flags = generator.compileFilter("{ t -> def flags = ['keep': false]\n if (t.env == 'prod') "
            + "{ flags.keep = t.job == 'api' } else { false } }");
        assertTrue(flags.test(Map.of("env", "prod", "job", "api")));
        assertFalse(flags.test(Map.of("env", "prod", "job", "x")));
    }

    @Test
    void aNumberLiteralKeepsItsGroovyType() throws Exception {
        assertEquals("v3000000000", tagged("m.tag({ t -> t.r = 'v' + 3000000000 })", labels()).get("r"));
        assertEquals("v-7", tagged("m.tag({ t -> t.r = 'v' + -7 })", labels()).get("r"));
        assertEquals("v8", tagged("m.tag({ t -> t.r = 'v' + 010 })", labels()).get("r"), "010 is octal 8, as Groovy");
        assertEquals("-9223372036854775808", tagged("m.tag({ t -> long missing = -9223372036854775808\n "
            + "t.r = String.valueOf(missing) })", labels()).get("r"));
    }

    @Test
    void decimalLiteralsAreDoubles() throws Exception {
        assertEquals("0.30000000000000004", tagged("m.tag({ t -> Object a = 0.1\n Object b = 0.2\n"
            + " t.r = String.valueOf(a + b) })", labels()).get("r"));
        // beside a whole number too
        assertEquals("4.0", tagged("m.tag({ t -> t.r = String.valueOf(Long.parseLong(t.k) * 2.0) })", labels("k", "2"))
            .get("r"));
        assertTrue(generator.compileFilter("{ t -> Long.parseLong(t.start_ns) + 1000.0 < Long.parseLong(t.end_ns) }")
            .test(Map.of("start_ns", "1000", "end_ns", "2001")));
        assertTrue(generator.compileFilter("{ t -> Double.parseDouble(t.util) > 0.5 }").test(Map.of("util", "0.7")));
        assertTrue(generator.compileFilter("{ t -> Double.parseDouble(t.rate) > 00.5 }").test(Map.of("rate", "0.75")));
        assertTrue(generator.compileFilter("{ t -> Double.parseDouble(t.bytes) < 10000000000000000000.0 }")
            .test(Map.of("bytes", "9.5E18")), "a large decimal literal keeps its value");
        assertTrue(generator.compileFilter("{ t -> Integer.parseInt(t.latency_ms) / 1000.0 > 1.5 }")
            .test(Map.of("latency_ms", "1600")));
        assertTrue(generator.compileFilter("{ t -> Long.parseLong(t.elapsed_ns) / 60000000000.0 > 1 }")
            .test(Map.of("elapsed_ns", "90000000000")));
        assertTrue(generator.compileFilter("{ t -> Integer.parseInt(t.e) * 1000.0 > 1600000000000 }")
            .test(Map.of("e", "1700000000")), "a decimal literal makes it a double: no int overflow");
        assertTrue(generator.compileFilter("{ t -> Integer.parseInt(t.errors) * 100.0 / Integer.parseInt(t.total) > 5 }")
            .test(Map.of("errors", "6", "total", "100")));
        assertTrue(generator.compileFilter("{ t -> Long.parseLong(t.bytes) / (1024.0 * 1024.0) > 10 }")
            .test(Map.of("bytes", "20971520")));
        assertTrue(generator.compileFilter("{ t -> Long.parseLong(t.bytes) / (1024.0 * 1024.0) > 10 }")
            .test(Map.of("bytes", "11010048")));
        assertTrue(generator.compileFilter("{ t -> Double.parseDouble(t.latency_ms) / 1000 > 1 }")
            .test(Map.of("latency_ms", "1500")));
    }

    @Test
    void divisionFollowsGroovy() throws Exception {
        assertEquals("v5.0", tagged("m.tag({ tags -> tags.r = 'v' + (5 / 2 * 2) })", labels()).get("r"));
        assertEquals("v3.5", tagged("m.tag({ t -> t.r = 'v' + (Integer.parseInt(t.a) / 2) })", labels("a", "7"))
            .get("r"));
        assertEquals("3", tagged("m.tag({ t -> int x = 7 / 2\n t.r = String.valueOf(x) })", labels()).get("r"));
        assertEquals("-3", tagged("m.tag({ t -> int x = -7 / 2\n t.r = String.valueOf(x) })", labels()).get("r"));
        assertEquals("2", tagged("m.tag({ t -> long x = Long.parseLong(t.a) / Long.parseLong(t.b)\n "
            + "t.r = String.valueOf(x) })", labels("a", "10", "b", "4")).get("r"));
        assertTrue(generator.compileFilter("{ t -> t.a.length() / 2 == 1.5 }").test(Map.of("a", "abc")));
        assertThrows(ArithmeticException.class, () -> generator.compileFilter(
            "{ t -> Integer.parseInt(t.a) / Integer.parseInt(t.b) > 1 }").test(Map.of("a", "7", "b", "0")),
            "whole numbers divided by zero throw, as in Groovy");
    }

    @Test
    void quotientsAreTheBigDecimalGroovyHolds() throws Exception {
        final Map<String, String> ns = labels("ns", "1700000000999999999");
        assertEquals("30000000", tagged("m.tag({ t -> long n = Long.parseLong(t.ns)\n long minute = n / 60000000000\n "
            + "t.r = String.valueOf(minute) })", labels("ns", "1799999999999999999")).get("r"), "Groovy's rounded quotient");
        assertEquals("-30000000", tagged("m.tag({ t -> long n = Long.parseLong(t.ns)\n long minute = -(n / 60000000000)\n "
            + "t.r = String.valueOf(minute) })", labels("ns", "1799999999999999999")).get("r"));
        assertTrue(generator.compileFilter("{ t -> Long.parseLong(t.ns) / 1000000 < 1700000001000 }").test(ns),
            "compared exactly");
        assertEquals("1700000000999", tagged("m.tag({ t -> long ms = t.a ? Long.parseLong(t.ns) / 1000000 : "
            + "Long.parseLong(t.ns) / 1000\n t.r = String.valueOf(ms) })", labels("ns", "1700000000999999999", "a", "1"))
            .get("r"), "two quotient arms");
        assertEquals("1700000000999", tagged("m.tag({ t -> def ms = Long.parseLong(t.ns) / 1000000\n def copy = ms\n "
            + "long out = copy\n t.r = String.valueOf(out) })", ns).get("r"));
        assertEquals("1700000000999.999999", tagged("m.tag({ t -> t.r = String.valueOf(Long.parseLong(t.ns) / 1000000) })",
            ns).get("r"), "printed as Groovy prints it");
        assertEquals("2", tagged("m.tag({ t -> t.r = Long.parseLong(t.n) / 5 })", labels("n", "10")).get("r"));
        assertEquals("250.0", tagged("m.tag({ t -> t.r = String.valueOf((Long.parseLong(t.n) / 4) * 100) })",
            labels("n", "10")).get("r"));
        assertThrows(ArithmeticException.class, () -> tagged("m.tag({ t -> t.r = String.valueOf(Long.parseLong(t.n) / "
            + "Long.parseLong(t.d)) })", labels("n", "10", "d", "0")));
        assertTrue(generator.compileFilter("{ t -> Long.parseLong(t.bytes) / 1048576 / 86400 < 1 }")
            .test(labels("bytes", "90596966399")), "a chained quotient keeps the operands' scale");
        assertEquals("-2.5", tagged("m.tag({ t -> t.r = String.valueOf(-(Long.parseLong(t.n) / 4)) })",
            labels("n", "10")).get("r"));
    }

    @Test
    void quotientsNarrowIntoDeclaredNumbers() throws Exception {
        assertEquals("-2147483648", tagged("m.tag({ t -> int kib = Long.parseLong(t.bytes) / 1024\n "
            + "t.r = String.valueOf(kib) })", labels("bytes", "2199023255552")).get("r"),
            "a quotient is a BigDecimal to Groovy: into an int it wraps");
        assertEquals("-2147483648", tagged("m.tag({ t -> int kib = t.bytes ? Long.parseLong(t.bytes) / 1024 : 0\n "
            + "t.r = String.valueOf(kib) })", labels("bytes", "2199023255552")).get("r"), "a quotient wraps");
        assertEquals("-2147483648", tagged("m.tag({ t -> def q = Long.parseLong(t.bytes) / 1024\n int kib = q\n "
            + "t.r = String.valueOf(kib) })", labels("bytes", "2199023255552")).get("r"));
        assertEquals("1700000000999", tagged("m.tag({ t -> long ms = t.ns ? Long.parseLong(t.ns) / 1000000 : 0\n "
            + "t.r = String.valueOf(ms) })", labels("ns", "1700000000999999999")).get("r"));
        assertEquals("-1700000000999", tagged("m.tag({ t -> long ms = -(Long.parseLong(t.ns) / 1000000)\n "
            + "t.r = String.valueOf(ms) })", labels("ns", "1700000000999999999")).get("r"));
        assertEquals("-2147483648", tagged("m.tag({ t -> Object v = t.override ? Double.parseDouble(t.override) : "
            + "Long.parseLong(t.bytes) / 1024\n int n = v\n t.r = String.valueOf(n) })",
            labels("bytes", "2199023255552")).get("r"), "the quotient arm wraps");
        assertEquals("9007199254740993", tagged("m.tag({ t -> def ns = t.total ? Long.parseLong(t.total) : 0\n "
            + "long avg = ns / 3\n t.r = String.valueOf(avg) })", labels("total", "27021597764222981")).get("r"));
        assertEquals("1700000000999", tagged("m.tag({ t -> def ms = Long.parseLong(t.ns) / 1000000\n long out = ms\n "
            + "t.r = String.valueOf(out) })", labels("ns", "1700000000999999999")).get("r"), "a def holds the exact quotient");
        assertEquals("1700000000999", tagged("m.tag({ t -> long ms = (Long.parseLong(t.ns) / 1000000) ?: 0\n "
            + "t.r = String.valueOf(ms) })", labels("ns", "1700000000999999999")).get("r"));
        assertEquals("30000000", tagged("m.tag({ t -> def ns = t.ns ? Long.parseLong(t.ns) : 0\n "
            + "long min = ns / 60000000000\n t.r = String.valueOf(min) })", labels("ns", "1799999999999999999")).get("r"),
            "a non-terminating quotient rounds to Groovy's ten decimals");
        assertEquals("-2147483648", tagged("m.tag({ t -> int kib = t.bytes ? Long.parseLong(t.bytes) / 1024 : "
            + "Double.parseDouble(t.f)\n t.r = String.valueOf(kib) })", labels("bytes", "2199023255552")).get("r"));
        assertEquals("-2147483648", tagged("m.tag({ t -> def x = t.p ? Long.parseLong(t.p) : 0\n "
            + "int kib = x ?: (Long.parseLong(t.bytes) / 1024)\n t.r = String.valueOf(kib) })",
            labels("p", "0", "bytes", "2199023255552")).get("r"));
        assertEquals("-2147483648", tagged("m.tag({ t -> int kib = (t.f ? Double.parseDouble(t.f) : null) ?: "
            + "Long.parseLong(t.bytes) / 1024\n t.r = String.valueOf(kib) })", labels("bytes", "2199023255552")).get("r"));
        assertEquals("1700000000999", tagged("m.tag({ t -> long ms = Long.parseLong(t.ns) / 1000000\n "
            + "t.r = String.valueOf(ms) })", labels("ns", "1700000000999999999")).get("r"), "truncated exactly");
        assertEquals("-2147483647", tagged("m.tag({ t -> int n = (t.p ? Long.parseLong(t.bytes) / 1024 : 0.0) + 1\n "
            + "t.r = String.valueOf(n) })", labels("p", "1", "bytes", "2199023255552")).get("r"));
        assertEquals("-2147483648", tagged("m.tag({ t -> int kib = (Long.parseLong(t.bytes) / 1024) ?: 0\n "
            + "t.r = String.valueOf(kib) })", labels("bytes", "2199023255552")).get("r"));
        assertEquals("-2147483648", tagged("m.tag({ t -> def bytes = t.bytes ? Long.parseLong(t.bytes) : 0\n "
            + "int kib = bytes / 1024\n t.r = String.valueOf(kib) })", labels("bytes", "2199023255552")).get("r"));
        assertEquals("-2147483648", tagged("m.tag({ t -> def kib = 0\n if (t.bytes) { kib = Long.parseLong(t.bytes) "
            + "/ 1024 }\n int n = kib\n t.r = String.valueOf(n) })", labels("bytes", "2199023255552")).get("r"),
            "a quotient held as Object wraps");
    }

    @Test
    void narrowingIntoDeclaredNumbers() throws Exception {
        assertEquals("2147483647", tagged("m.tag({ t -> int n = 3000000000.0\n t.r = String.valueOf(n) })", labels())
            .get("r"), "a decimal literal is a double, narrowed as one");
        assertEquals("1000", tagged("m.tag({ t -> int scale = 1000.0\n t.r = String.valueOf(scale) })", labels())
            .get("r"));
        assertEquals("12", tagged("m.tag({ t -> int bucket = Double.parseDouble(t.latency_ms)\n "
            + "t.b = String.valueOf(bucket) })", labels("latency_ms", "12.75")).get("b"));
        assertEquals("12", tagged("m.tag({ t -> int bucket = t.latency_ms ? Double.parseDouble(t.latency_ms) : 0\n "
            + "t.r = String.valueOf(bucket) })", labels("latency_ms", "12.75")).get("r"));
        assertTrue(generator.compileFilter("{ t -> int n = 0; n = t.epoch ? Long.parseLong(t.epoch) : 0; n == 1 }")
            .test(Map.of("epoch", "1")), "a declared int takes the selected Long converted, as Groovy assigns it");
        assertEquals("2147483647", tagged("m.tag({ t -> int n = Double.parseDouble(t.gib) * 1073741824.0\n "
            + "t.r = String.valueOf(n) })", labels("gib", "3")).get("r"), "a Double saturates into an int");
        assertEquals("2147483647", tagged("m.tag({ t -> int n = t.gib ? Double.parseDouble(t.gib) * 1073741824.0 : 0.0"
            + "\n t.r = String.valueOf(n) })", labels("gib", "3")).get("r"), "a Double arm saturates");
        assertEquals("2147483647", tagged("m.tag({ t -> def n = 0.0\n if (t.gib) { n = Double.parseDouble(t.gib) * "
            + "1073741824.0 }\n int cap = n\n t.r = String.valueOf(cap) })", labels("gib", "3")).get("r"));
    }

    @Test
    void typedLocalsHoldTheirValues() throws Exception {
        final MalFilter local = generator.compileFilter("{ t -> def n = t.a.length(); return n == 1 }");
        assertTrue(local.test(Map.of("a", "x")));
        assertFalse(local.test(Map.of("a", "xy")));
        final MalFilter booleanLocal = generator.compileFilter("{ t -> boolean b = t.a == 'x'; b }");
        assertTrue(booleanLocal.test(Map.of("a", "x")));
        final MalFilter ok = generator.compileFilter("{ t -> boolean ok = false; if (t.a) { ok = t.job == 'ok' }\n ok }");
        assertTrue(ok.test(Map.of("a", "x", "job", "ok")));
        final Map<?, ?> next = tagged("m.tag({ t -> Integer n = t.a.length(); ['next': n + 1] })", labels("a", "abc"));
        assertEquals("4", next.get("next"));
        // into a String local Groovy converts the value
        final MalFilter port = generator.compileFilter(
            "{ t -> String port = t.getOrDefault('port', Integer.valueOf(443))\n port == '443' }");
        assertTrue(port.test(Map.of()));
        assertEquals("[a, b]", tagged("m.tag({ t -> String path = t.getOrDefault('path', t.parts.split(':'))\n "
            + "t.r = path })", labels("parts", "a:b")).get("r"));
        assertEquals("443", tagged("m.tag({ t -> String port = 443\n t.port = port })", labels()).get("port"));
        assertEquals("[env]", tagged("m.tag({ t -> String keys = t.keySet()\n t.r = String.valueOf(keys) })",
            labels("env", "e")).get("r"));
    }

    @Test
    void defLocalsWidenToObject() throws Exception {
        final MalFilter reassigned = generator.compileFilter("{ t -> def n = 0; n = t.a.length(); n == 3 }");
        assertTrue(reassigned.test(Map.of("a", "abc")));
        final Map<?, ?> next = tagged("m.tag({ t -> def n = t.a.length(); ['next': n + 1] })", labels("a", "abc"));
        assertEquals("4", next.get("next"), "int arithmetic (not concatenation), written as its string");
        final MalFilter matched = generator.compileFilter(
            "{ t -> def matched = false; if (t.a == 'x') { matched = true }\n matched }");
        assertTrue(matched.test(Map.of("a", "x")));
        assertFalse(matched.test(Map.of("a", "y")));
        assertEquals("global", tagged("m.tag({ t -> def location = null\n if (t.region) { location = t.region.trim() }"
            + "\n t.loc = location ?: 'global' })", labels()).get("loc"));
        assertTrue(generator.compileFilter("{ t -> def status = t.status\n if (t.status) { status = "
            + "Integer.valueOf(t.status) }\n Integer.valueOf(429).equals(status) }").test(Map.of("status", "429")));
        assertTrue(generator.compileFilter("{ t -> def n = 0; n = Long.parseLong('1700000000000'); n == 1700000000000 }")
            .test(Map.of()), "a def holds the Long assigned to it");
        assertEquals("inherit", tagged("m.tag({ t -> def enabled = false\n enabled = t.flag?.equalsIgnoreCase('true')"
            + "\n t.r = enabled == null ? 'inherit' : (enabled ? 'on' : 'off') })", labels()).get("r"));
        assertEquals("9007199254740993", tagged("m.tag({ t -> def id = Double.parseDouble(t.a)\n id = "
            + "Long.parseLong(t.b)\n t.r = String.valueOf(id) })", labels("a", "1", "b", "9007199254740993"))
            .get("r"));
        assertEquals("5", tagged("m.tag({ t -> def v = 0\n v = t.status.charAt(0)\n t.r = String.valueOf(v) })",
            labels("status", "503")).get("r"));
        assertEquals("123", tagged("m.tag({ t -> def x = Double.parseDouble(t.a)\n x = 123\n t.r = String.valueOf(x) })",
            labels("a", "1.5")).get("r"));
        assertEquals("5", tagged("m.tag({ t -> def total = 0\n total = total + Long.parseLong(t.a)\n "
            + "t.r = String.valueOf(total) })", labels("a", "5")).get("r"));
        assertEquals("a", tagged("m.tag({ t -> def path = t.path.split('/')\n path = path[0]\n t.r = path })",
            labels("path", "a/b")).get("r"));
    }

    @Test
    void branchesDeclareInTheirOwnScope() throws Exception {
        assertEquals("us", tagged("m.tag({ t -> if (t.a) { def p = (t.a =~ /[a-z]+/) } else { def p = t.b.split('-')"
            + "\n t.r = p[0] } })", labels("b", "us-east")).get("r"), "a new declaration starts afresh");
        final MalFilter scoped = assertTimeoutPreemptively(Duration.ofSeconds(20), () -> generator.compileFilter(
            "{ t -> if (t.legacy) { def n = t.legacy\n n == 'y' } else { long n = 0\n n = Integer.valueOf(t.count)\n"
                + " n > 2 } }"), "a def in one branch and a typed local in the other do not regenerate forever");
        assertTrue(scoped.test(Map.of("count", "3")));
        assertTrue(generator.compileFilter("{ t -> if (t.legacy) { def n = t.count\n n = Long.parseLong(t.count)\n "
            + "n > 100 } else { def n = Integer.parseInt(t.count)\n n + 1 > 100 } }").test(Map.of("count", "150")));
    }

    @Test
    void ternaryArmsMergeAndConvert() throws Exception {
        assertEquals("n3", tagged("m.tag({ t -> int n = t.a ? 3 : 0; t.out = 'n' + n })", labels("a", "x")).get("out"));
        final MalFilter booleanArm = generator.compileFilter("{ t -> t.enabled ? t.job == 'ok' : false }");
        assertTrue(booleanArm.test(Map.of("enabled", "y", "job", "ok")));
        assertFalse(booleanArm.test(Map.of("job", "ok")));
        final MalFilter mixed = generator.compileFilter(
            "{ t -> Object display = t.a ? t.a.length() : 'missing'; display.toString() == '3' }");
        assertTrue(mixed.test(Map.of("a", "abc")));
        assertFalse(mixed.test(Map.of()));
        final MalFilter epoch = generator.compileFilter(
            "{ t -> long n = 0; n = t.epoch ? Long.parseLong(t.epoch) : 0; n == 1700000000000 }");
        assertTrue(epoch.test(Map.of("epoch", "1700000000000")));
        assertEquals("3", tagged("m.tag({ t -> t.r = (t.a ? t.a.length() : null).toString() })",
            labels("a", "abc")).get("r"));
        final MalFilter nullableDef = generator.compileFilter("{ t -> def s = t.a ? t.a.trim() : null; s == 'x' }");
        assertTrue(nullableDef.test(Map.of("a", " x ")));
        final MalFilter ts = generator.compileFilter("{ t -> long ts = t.created ? Long.parseLong(t.created) "
            + ": t.updated ? Long.parseLong(t.updated) : 0\n ts == 1710000000000 }");
        assertTrue(ts.test(Map.of("updated", "1710000000000")));
        final MalFilter port = generator.compileFilter(
            "{ t -> String port = t.secure ? t.getOrDefault('port', Integer.valueOf(443)) : '80'\n port == '80' }");
        assertTrue(port.test(Map.of()));
        assertFalse(port.test(Map.of("secure", "y")));
        assertEquals("443", tagged("m.tag({ t -> String port = t.secure ? 443 : '80'\n t.port = port })",
            labels("secure", "y")).get("port"));
        assertEquals("80", tagged("m.tag({ t -> String port = t.secure ? 443 : 80\n t.port = port })", labels())
            .get("port"));
        assertEquals("api", tagged("m.tag({ t -> Object fallback = t.job\n t.r = (t.custom ? t.host : fallback).trim() })",
            labels("job", " api ")).get("r"));
        assertEquals("web", tagged("m.tag({ t -> Object fallback = t.job\n t.r = (t.custom ? t.host : fallback).trim() })",
            labels("custom", "y", "host", " web ")).get("r"));
        final MalFilter ms = generator.compileFilter("{ t -> (t.ms != null ? Double.parseDouble(t.ms) : 0.0) / 1000 > 1 }");
        assertTrue(ms.test(Map.of("ms", "1500")));
        assertFalse(ms.test(Map.of()));
        assertTrue(generator.compileFilter("{ t -> (t.ratio != null ? Double.parseDouble(t.ratio) : 0) * 100 > 80 }")
            .test(Map.of("ratio", "0.9")));
        assertTrue(generator.compileFilter("{ t -> (t.delta != null ? Long.parseLong(t.delta) : 0) + "
            + "Long.parseLong(t.base) > 1000 }").test(Map.of("base", "1001")));
        assertEquals("3", tagged("m.tag({ t -> t.r = (t.a ? t.a.length() : 'x').toString() })", labels("a", "abc"))
            .get("r"));
        assertEquals("x", tagged("m.tag({ t -> t.r = (t.a ? t.a.length() : 'x').toString() })", labels()).get("r"));
        assertTrue(generator.compileFilter("{ t -> ((t.end_ns ? Long.parseLong(t.end_ns) : Double.parseDouble(t.legacy))"
            + " - Long.parseLong(t.start_ns)) == 1 }")
            .test(Map.of("end_ns", "1700000000000000001", "start_ns", "1700000000000000000")),
            "the selected Long arm keeps integral arithmetic");
        assertEquals("0", tagged("m.tag({ t -> char digit = t.status ? t.status.charAt(0) : '0'\n "
            + "t.r = String.valueOf(digit) })", labels()).get("r"));
        assertEquals("5", tagged("m.tag({ t -> char digit = t.status ? t.status.charAt(0) : 48\n "
            + "t.r = String.valueOf(digit) })", labels("status", "503")).get("r"));
        assertEquals("0", tagged("m.tag({ t -> char digit = t.status ? t.status.charAt(0) : 48\n "
            + "t.r = String.valueOf(digit) })", labels()).get("r"));
    }

    @Test
    void ternaryAndElvisAsStatements() throws Exception {
        assertEquals(Map.of("a", "x"), tagged("m.tag({ t -> t.a ? 1 : 2 })", labels("a", "x")));
        assertEquals(Map.of("a", "x"), tagged("m.tag({ t -> t.a ? 1 : null })", labels("a", "x")));
        final Map<String, String> redacted = tagged("m.tag({ t -> t.redact ? t.remove('token') : "
            + "t.remove('legacy_token')\n t.source = 'normalized' })",
            labels("redact", "y", "token", "x", "legacy_token", "z"));
        assertFalse(redacted.containsKey("token"));
        assertEquals("z", redacted.get("legacy_token"));
        final Map<String, String> nested = tagged("m.tag({ t -> t.redact ? (t.internal ? t.remove('debug') : "
            + "t.remove('token')) : t.remove('legacy')\n t.source = 'n' })",
            labels("redact", "y", "token", "x", "debug", "d", "legacy", "l"));
        assertFalse(nested.containsKey("token"));
        assertEquals("d", nested.get("debug"));
        final Map<String, String> mapArm = tagged("m.tag({ t -> t.redact ? ['old': t.remove('token'), 'debug': "
            + "t.remove('debug')] : t.remove('legacy')\n t.s = 'n' })",
            labels("redact", "y", "token", "x", "debug", "d", "legacy", "l"));
        assertFalse(mapArm.containsKey("token"));
        assertFalse(mapArm.containsKey("debug"));
        assertEquals("l", mapArm.get("legacy"));
        assertThrows(IllegalArgumentException.class, () -> generator.compile("t", "m.tag({ t -> t.enabled ? "
            + "t.remove('secret') : misspelledFallback\n t.r = 'ok' })"));
    }

    @Test
    void groovyTruthAndElvis() throws Exception {
        final MalFilter length = generator.compileFilter("{ t -> t.a?.length() }");
        assertTrue(length.test(Map.of("a", "x")));
        assertFalse(length.test(Map.of("a", "")));
        assertFalse(length.test(Map.of()));
        final MalFilter truth = generator.compileFilter("{ t -> boolean ok = t.raw.length(); ok }");
        assertTrue(truth.test(Map.of("raw", "svc-dev")));
        final MalFilter fallback = generator.compileFilter("{ t -> String job = t.job ?: 'default'; job == 'default' }");
        assertTrue(fallback.test(Map.of()));
        assertFalse(fallback.test(Map.of("job", "x")));
        final MalFilter elvisInt = generator.compileFilter("{ t -> def n = t.a.length() ?: 1; n > 0 }");
        assertTrue(elvisInt.test(Map.of("a", "")));
        final MalFilter elvis = generator.compileFilter("{ t -> def n = t.job?.length() ?: 0\n n == 0 }");
        assertTrue(elvis.test(Map.of()));
        assertEquals("j", tagged("m.tag({ t -> t.svc = t.service ?: t.get('job') })", labels("job", "j")).get("svc"));
        assertEquals("unknown", tagged("m.tag({ t -> t.r = t.a ?: t.b ?: 'unknown' })", labels()).get("r"));
        assertEquals("b1", tagged("m.tag({ t -> t.r = t.a ?: t.b ?: 'unknown' })", labels("b", "b1")).get("r"));
        assertEquals("k8s::p", tagged("m.tag({ t -> t.r = t.svc ?: 'k8s::' + t.pod })", labels("pod", "p")).get("r"));
        assertEquals("n/p", tagged("m.tag({ t -> t.r = t.svc ?: \"${t.ns}/${t.pod}\" })", labels("ns", "n", "pod", "p"))
            .get("r"));
        assertEquals("unknown", tagged("m.tag({ t -> t.r = t.service ?: t.job ?: 'unknown' })", labels()).get("r"));
        assertEquals("pod-p", tagged("m.tag({ t -> t.svc = t.service ?: (t.pod ? 'pod-' + t.pod : 'unknown') })",
            labels("pod", "p")).get("svc"));
        final MalFilter lazy = generator.compileFilter("{ t -> Integer fallback = null\n "
            + "int limit = Integer.parseInt(t.limit) ?: (fallback + 1)\n limit >= 5 }");
        assertFalse(lazy.test(Map.of("limit", "2")), "the unused fallback is not evaluated");
        assertEquals("j", tagged("m.tag({ t -> t.svc = t.service ?: (t.useJob ? t.job : t.app) })",
            labels("useJob", "y", "job", "j")).get("svc"));
        assertEquals("x", tagged("m.tag({ t -> t.r = t.a ?: t.missing.trim() })", labels("a", "x")).get("r"),
            "the unused fallback (which would throw) is not evaluated");
        assertTrue(generator.compileFilter("{ t -> String[] p = t.raw.split(':'); (t.env ?: p[1]) == 'prod' }")
            .test(Map.of("raw", "a:prod")));
        final MalFilter rate = generator.compileFilter("{ t -> (Double.parseDouble(t.rate) ?: 1.0) * 100 > 50 }");
        assertTrue(rate.test(Map.of("rate", "0.0")), "0.0 is falsy: the fallback 1.0 applies");
        assertFalse(rate.test(Map.of("rate", "0.3")));
        assertEquals("api", tagged("m.tag({ t -> Object fallback = t.job\n t.r = (t.host ?: fallback).trim() })",
            labels("job", " api ")).get("r"));
        assertTrue(generator.compileFilter("{ t -> def rate = Double.parseDouble(t.rate) ?: 1\n rate * 100 > 50 }")
            .test(Map.of("rate", "0.75")));
    }

    @Test
    void safeNavigation() throws Exception {
        final MalFilter startsWith = generator.compileFilter("{ t -> t.a?.startsWith('x') }");
        assertTrue(startsWith.test(Map.of("a", "xa")));
        assertFalse(startsWith.test(Map.of("a", "ya")));
        assertFalse(startsWith.test(Map.of()));
        final MalFilter safeLength = generator.compileFilter("{ t -> def n = t.a?.length(); n == 3 }");
        assertTrue(safeLength.test(Map.of("a", "abc")));
        assertFalse(safeLength.test(Map.of()));
        assertEquals("api", tagged("m.tag({ t -> t.svc = t.get('service')?.trim() ?: 'unknown' })",
            labels("service", " api ")).get("svc"));
        assertEquals("unknown", tagged("m.tag({ t -> t.svc = t.get('service')?.trim() ?: 'unknown' })",
            labels()).get("svc"));
        assertTrue(generator.compileFilter("{ t -> t?.env == 'prod' }").test(Map.of("env", "prod")));
        assertEquals("unknown", tagged("m.tag({ t -> t.r = t.getOrDefault('service', '')?.trim() ?: 'unknown' })",
            labels()).get("r"));
        assertEquals("job", tagged("m.tag({ t -> t.svc = t.service?.trim() ?: t.job?.trim() ?: 'unknown' })",
            labels("job", " job ")).get("svc"));
        assertEquals("api", tagged("m.tag({ t -> t.svc = t.service?.trim()?.toLowerCase() ?: 'unknown' })",
            labels("service", " API ")).get("svc"));
        assertEquals("unknown", tagged("m.tag({ t -> t.svc = t.service?.trim()?.toLowerCase() ?: 'unknown' })",
            labels()).get("svc"));
        final Map<String, String> once = tagged("m.tag({ tags -> tags.r = tags.remove('a')?.trim() })",
            labels("a", " x "));
        assertEquals("x", once.get("r"));
        assertFalse(once.containsKey("a"));
        final Map<String, String> byKey = tagged("m.tag({ t -> t.r = t[t.remove('key')]?.trim() })",
            labels("key", "svc", "svc", " api "));
        assertEquals("api", byKey.get("r"));
        assertFalse(byKey.containsKey("key"), "the key is removed once");
        assertEquals("x", tagged("m.tag({ t -> t.r = t.remove('a').split(':')[0]?.trim() })", labels("a", " x :y"))
            .get("r"));
        assertEquals("api", tagged("m.tag({ t -> Object fallback = t.job\n t.r = t.getOrDefault('svc', fallback)?.trim()"
            + " ?: 'unknown' })", labels("job", " api ")).get("r"));
        assertEquals("2", tagged("m.tag({ t -> t.r = String.valueOf(t.ids?.split(',')?.size()) })", labels("ids", "a,b"))
            .get("r"));
    }

    @Test
    void aLabelReadIsAString() throws Exception {
        final MalFilter get = generator.compileFilter("{ t -> def job = t.get('job'); job.startsWith('o') }");
        assertTrue(get.test(Map.of("job", "ok")));
        assertEquals("UNKNOWN", tagged(
            "m.tag({ t -> String svc = t.getOrDefault('service', 'unknown'); t.svc = svc.toUpperCase() })",
            labels()).get("svc"));
        assertEquals("unknown", tagged("m.tag({ t -> t.svc = t.getOrDefault('service', 'unknown') })", labels())
            .get("svc"));
        assertEquals("api", tagged("m.tag({ t -> t.svc = t.getOrDefault('service', t.job) })", labels("job", "api"))
            .get("svc"));
    }

    @Test
    void equalityByValue() throws Exception {
        assertTrue(generator.compileFilter("{ t -> t.code == 503 }").test(Map.of("code", "503")), "\"503\" == 503");
        assertFalse(generator.compileFilter("{ t -> t.code == 503 }").test(Map.of("code", "abc")));
        assertFalse(generator.compileFilter("{ t -> t.code.charAt(0) == 65 }").test(Map.of("code", "A")),
            "a character is the one-character string it is");
        assertTrue(generator.compileFilter("{ t -> t.code.charAt(0) == 1 }").test(Map.of("code", "1")));
    }

    @Test
    void orderingOfStringsCharactersAndNumbers() throws Exception {
        final MalFilter status = generator.compileFilter("{ t -> t.code.charAt(0) >= '5' }");
        assertTrue(status.test(Map.of("code", "503")));
        assertFalse(status.test(Map.of("code", "404")));
        final MalFilter priority = generator.compileFilter("{ t -> t.priority < 5 }");
        assertTrue(priority.test(Map.of("priority", "1")), "a string against a number reads as a number");
        assertFalse(priority.test(Map.of("priority", "7")));
        assertThrows(IllegalArgumentException.class, () -> priority.test(Map.of("priority", "high")),
            "a string that is no number fails");
        assertTrue(generator.compileFilter("{ t -> t.a > t.b }").test(Map.of("a", "10", "b", "2")),
            "two strings that read as numbers compare as numbers");
        assertTrue(generator.compileFilter("{ t -> t.a < t.b }").test(Map.of("a", "abc", "b", "abd")),
            "otherwise two strings compare as text");
        assertTrue(generator.compileFilter("{ t -> '3' < 5 }").test(Map.of()));
        assertTrue(generator.compileFilter("{ t -> Integer.parseInt(t.priority) < 5 }").test(Map.of("priority", "1")));
        final MalFilter boxed = generator.compileFilter("{ t -> Integer n = Integer.parseInt(t.status); n >= 500 }");
        assertTrue(boxed.test(Map.of("status", "503")));
        final MalFilter nullable = generator.compileFilter("{ t -> t.a?.length() > 0 }");
        assertTrue(nullable.test(Map.of("a", "x")));
        assertFalse(nullable.test(Map.of()), "null orders below every value");
        final MalFilter code = generator.compileFilter("{ t -> t.code.charAt(0) > t.limit }");
        assertFalse(code.test(Map.of("code", "503", "limit", "500")));
        assertFalse(code.test(Map.of("code", "404", "limit", "500")));
        assertEquals("true", tagged("m.tag({ t -> t.r = String.valueOf(t.code.charAt(0) < t.limit) })",
            labels("code", "9", "limit", "10")).get("r"), "a character is the one-character string it is");
        final MalFilter digit = generator.compileFilter("{ t -> t.code.charAt(0) > 4 }");
        assertTrue(digit.test(Map.of("code", "503")), "a character reads as the number it is: '5' > 4");
        assertFalse(digit.test(Map.of("code", "404")));
        assertThrows(IllegalArgumentException.class, () -> digit.test(Map.of("code", "x01")),
            "a character that is no number fails in an ordering");
        assertThrows(IllegalArgumentException.class, () -> generator.compileFilter(
            "{ t -> Long.parseLong(t.ns) / 1000 > '100ms' }"));
    }

    @Test
    void nanAndNegativeZero() throws Exception {
        assertFalse(generator.compileFilter("{ t -> Double.parseDouble(t.load) >= 10 }").test(Map.of("load", "NaN")),
            "a NaN compares false, as Java and MAL always compared it");
        assertFalse(generator.compileFilter("{ t -> Double.parseDouble(t.n) < 0 }").test(Map.of("n", "-0.0")),
            "-0.0 equals 0, as Java compares it");
        assertFalse(generator.compileFilter("{ t -> Double.parseDouble(t.load) >= t.limit }")
            .test(Map.of("load", "NaN", "limit", "10")), "a NaN against numeric text compares false too");
        final MalFilter nan = generator.compileFilter("{ t -> Double.parseDouble(t.rate) > Double.parseDouble(t.limit) }");
        assertFalse(nan.test(Map.of("rate", "NaN", "limit", "0.0")));
        assertFalse(generator.compileFilter("{ t -> Double.parseDouble(t.a) < Double.parseDouble(t.b) }")
            .test(Map.of("a", "-0.0", "b", "0.0")));
        assertFalse(generator.compileFilter("{ t -> Double.valueOf(t.rate) > Double.valueOf(t.limit) }")
            .test(Map.of("rate", "NaN", "limit", "0")));
        assertFalse(generator.compileFilter("{ t -> Double.valueOf(t.rate) >= 10 }").test(Map.of("rate", "NaN")),
            "a NaN compares false beside an int too, as Java compares it");
        assertEquals("no", tagged("m.tag({ t -> double v = Double.parseDouble(t.ratio)\n t.r = v == v ? 'yes' : 'no' })",
            labels("ratio", "NaN")).get("r"));
        assertEquals("eq", tagged("m.tag({ t -> double a = Double.parseDouble(t.a)\n double b = Double.parseDouble(t.b)"
            + "\n t.r = a == b ? 'eq' : 'ne' })", labels("a", "-0.0", "b", "0.0")).get("r"));
        assertEquals("ge", tagged("m.tag({ t -> int limit = 10\n double v = Double.parseDouble(t.v)\n "
            + "t.r = limit < v ? 'lt' : 'ge' })", labels("v", "NaN")).get("r"));
    }

    @Test
    void charactersComputeByTheirCode() throws Exception {
        assertTrue(generator.compileFilter("{ t -> t.code.charAt(0) - 48 > 4 }").test(Map.of("code", "503")));
        assertFalse(generator.compileFilter("{ t -> t.code.charAt(0) - 48 > 4 }").test(Map.of("code", "404")));
        assertTrue(generator.compileFilter("{ t -> def c = t.status?.charAt(0)\n c - 48 > 4 }")
            .test(Map.of("status", "503")));
        assertTrue(generator.compileFilter("{ t -> (t.status ? t.status.charAt(0) : 48) - 48 == 5 }")
            .test(Map.of("status", "503")));
        assertEquals("98", tagged("m.tag({ t -> def c = t.s.charAt(0)\n def n = 0\n n = c\n "
            + "t.r = String.valueOf(n + 1) })", labels("s", "a")).get("r"));
    }

    @Test
    void concatenationAndInterpolation() throws Exception {
        // an array is no text: joining one is refused, its toString() prints its elements
        assertThrows(IllegalArgumentException.class,
            () -> tagged("m.tag({ t -> t.r = 'parts=' + t.raw.split('-') })", labels("raw", "svc-prod")));
        assertEquals("parts=[svc, prod]", tagged("m.tag({ t -> t.r = 'parts=' + t.raw.split('-').toString() })",
            labels("raw", "svc-prod")).get("r"));
        // a $ that starts no interpolation is the character: a regex backreference, an end anchor
        assertEquals("b/a", tagged("m.tag({ t -> t.r = t.s.replaceAll(\"(a)-(b)\", \"$2/$1\") })", labels("s", "a-b"))
            .get("r"));
        assertTrue(generator.compileFilter("{ t -> t.s.matches(\"a-b$\") }").test(Map.of("s", "a-b")));
        assertEquals("svc-api", tagged("m.tag({ t -> String svc = t.service\n t.r = \"svc-${ svc }\" })",
            labels("service", "api")).get("r"));
        assertEquals("h:443", tagged("m.tag({ t -> def port = t.port ?: Integer.valueOf(443)\n t.endpoint = t.host "
            + "+ ':' + port })", labels("host", "h")).get("endpoint"));
        assertEquals("h:8080", tagged("m.tag({ t -> def port = t.port ?: Integer.valueOf(443)\n t.endpoint = t.host "
            + "+ ':' + port })", labels("host", "h", "port", "8080")).get("endpoint"));
        assertThrows(IllegalArgumentException.class, () -> tagged("m.tag({ t -> t.r = 'v' + (t.getOrDefault('a', 'b')"
            + " * 2) })", labels()), "a String times a number fails at run time (Groovy repeats the string)");
    }

    @Test
    void aTextJoinsOnlyOntoAStringANumberACharacterOrNull() throws Exception {
        assertEquals("null-api", tagged("m.tag({ t -> Object h = t.host\n Object suffix = t.suffix\n t.r = h + suffix })",
            labels("suffix", "-api")).get("r"));
        assertEquals("5-api", tagged("m.tag({ t -> Object n = Integer.valueOf(t.n)\n t.r = n + t.s })",
            labels("n", "5", "s", "-api")).get("r"));
        assertEquals("5-api", tagged("m.tag({ t -> Object n = Integer.valueOf(t.n)\n Object s = t.s\n "
            + "t.r = String.valueOf(n + s) })", labels("n", "5", "s", "-api")).get("r"));
        assertEquals("a-api", tagged("m.tag({ t -> Object c = t.a.charAt(0)\n t.r = c + t.s })",
            labels("a", "abc", "s", "-api")).get("r"));
        assertThrows(IllegalArgumentException.class, () -> tagged("m.tag({ t -> def tls = t.tls ? "
            + "(t.tls == 'true') : 'unknown'\n t.r = tls + '-backend' })", labels("tls", "true")),
            "a boolean + a string fails, as in Groovy");
        rejects("A boolean + a string has no meaning", () -> tagged("m.tag({ t -> Object b = t.a == 'x'\n "
            + "Object s = t.s\n t.r = String.valueOf(b + s) })", labels("a", "x", "s", "-api")));
        final MalFilter appended = generator.compileFilter(
            "{ t -> (t.getOrDefault('aliases', t.defaults.split(',')) + t.extra).contains('prod') }");
        assertThrows(IllegalArgumentException.class,
            () -> appended.test(Map.of("defaults", "production,staging", "extra", "dev")),
            "Groovy appends to the array: refused at run time rather than matched as text");
        rejects("+ on String[]", () -> tagged("m.tag({ t -> Object a = t.s.split(',')\n Object s = t.s\n "
            + "t.r = String.valueOf(a + s) })", labels("s", "a,b")));
        // any other value has no + with a text, whether the text is known when the rule compiles or only when it runs
        rejects("A ScopeType + a string has no meaning", () -> decorated(
            "{ me -> Object scope = me.scopeType\n me.attr0 = scope + me.serviceName }"));
        rejects("A ScopeType + a string has no meaning", () -> decorated(
            "{ me -> Object scope = me.scopeType\n Object name = me.serviceName\n "
                + "me.attr0 = String.valueOf(scope + name) }"));
        rejects("A ScopeType + a string has no meaning", () -> generator.compile("t",
            "m.service(['svc'], Layer.GENERAL).decorate({ me -> me.attr0 = me.scopeType + '-x' })"));
    }

    @Test
    void toStringPrintsAsGroovy() throws Exception {
        // toString() on a null fails, as it always did; ?.toString() reads it as null
        assertThrows(Exception.class, () -> tagged("m.tag({ t -> Object v = t.get('missing')\n t.r = v.toString() })",
            labels()));
        assertEquals("[a, b]", tagged("m.tag({ t -> t.r = t.ids.split(',')?.toString() })", labels("ids", "a,b"))
            .get("r"), "an array prints as Groovy prints it");
        assertEquals("3", tagged("m.tag({ t -> t.r = t.a.length().toString() })", labels("a", "abc")).get("r"));
        assertEquals("ab", MalRuntimeHelper.gstring(new char[] {'a', 'b'}));
    }

    @Test
    void indexingAndRegexMatches() throws Exception {
        final MalFilter last = generator.compileFilter("{ t -> String[] p = t.raw.split('-'); p[-1] == 'prod' }");
        assertTrue(last.test(Map.of("raw", "svc-prod")));
        assertFalse(last.test(Map.of("raw", "svc-dev")));
        assertEquals("west", tagged("m.tag({ t -> def m = (t.a =~ /([a-z]+)/); t.r = m[1][1] })",
            labels("a", "east-west")).get("r"));
        assertEquals("y", tagged("m.tag({ t -> if (t.path =~ /^\\/api\\/v1$/) { t.r = 'y' } })",
            labels("path", "/api/v1")).get("r"));
        final MalFilter noMatch = generator.compileFilter("{ t -> def m = (t.raw =~ /([a-z]+)-x/); m ? true : false }");
        assertFalse(noMatch.test(Map.of("raw", "svc-prod")));
        assertTrue(noMatch.test(Map.of("raw", "svc-x")));
        final MalFilter split = generator.compileFilter("{ t -> t.raw.split('-')[1] == 'prod' }");
        assertTrue(split.test(Map.of("raw", "svc-prod")));
        assertEquals("orders", tagged("m.tag({ t -> def m = (t.raw =~ /^api-/)\n if (m) { m = (t.raw =~ "
            + "/^api-(.*)$/)\n t.name = m[0][1] } })", labels("raw", "api-orders")).get("name"));
        final MalFilter branchRegex = generator.compileFilter("{ t -> def m = (t.env =~ /prod/)\n if (t.debug) { m = "
            + "(t.env =~ /(prod)/)\n false } else { m ? true : false } }");
        assertTrue(branchRegex.test(Map.of("env", "prod")));
        assertEquals("billing", tagged("m.tag({ t -> def m = (t.raw =~ /^api-(.*)$/)\n if (t.legacy) { m = (t.raw =~ "
            + "/^legacy-/)\n return t }\n t.group = m[0][1] })", labels("raw", "api-billing")).get("group"));
        // a match that is not there fails, as in Groovy: the rule checks the match first
        assertThrows(IndexOutOfBoundsException.class, () -> tagged("m.tag({ t -> def m = (t.raw =~ /api-(.*)/); "
            + "t.r = m[0][1] })", labels("raw", "web-x")));
        assertEquals("none", tagged("m.tag({ t -> def m = (t.raw =~ /api-(.*)/); t.r = m ? m[0][1] : 'none' })",
            labels("raw", "web-x")).get("r"));
        assertEquals("x", tagged("m.tag({ t -> def m = (t.raw =~ /api-(.*)/)\n if (m) { t.r = m[0][1] } })",
            labels("raw", "api-x")).get("r"));
        assertEquals("none", tagged("m.tag({ t -> def m = (t.raw =~ /api-(x)?(.*)/); t.r = m[0][1] ?: 'none' })",
            labels("raw", "api-y")).get("r"), "a group that took no part is null, as in Groovy");
        assertThrows(IndexOutOfBoundsException.class, () -> tagged("m.tag({ t -> def parts = "
            + "java.util.Arrays.asList(t.path.split('/'))\n t.r = parts[-2] ?: 'root' })", labels("path", "service")));
        assertEquals("C", tagged("m.tag({ t -> String last = t.s[-1]\n t.r = last.toUpperCase() })", labels("s", "abc"))
            .get("r"), "a string's element is the one-character string");
        assertEquals("cd", tagged("m.tag({ t -> String[] p = t.a.split('/')\n t.r = p[Integer.valueOf(t.i)] })",
            labels("a", "ab/cd", "i", "1")).get("r"), "a boxed index");
        assertEquals("b", tagged("m.tag({ t -> def x = t.x ? Integer.parseInt(t.x) : 1\n def p = t.s.split(',')\n "
            + "t.r = p[x] })", labels("s", "a,b")).get("r"), "a run-time Integer index");
        assertThrows(IllegalArgumentException.class, () -> tagged("m.tag({ t -> def x = t.x ? Long.parseLong(t.x) : 1\n "
            + "def p = t.s.split(',')\n t.r = p[x] })", labels("s", "a,b", "x", "0")), "a run-time Long index fails");
        assertThrows(IllegalArgumentException.class, () -> tagged("m.tag({ t -> def levels = "
            + "java.util.Arrays.asList(t.names.split(','))\n t.r = levels[t.status.startsWith('5')] })",
            labels("names", "client,server", "status", "503")), "a boolean is no index");
        assertThrows(IllegalArgumentException.class, () -> generator.compile("t", "m.tag({ t -> def p = "
            + "t.path.split('/')\n t.r = p[Integer.parseInt(t.i) / 2] })"), "a quotient is no index");
        assertThrows(IllegalArgumentException.class, () -> generator.compile("t", "m.tag({ t -> def p = "
            + "t.path.split('/')\n t.r = p[Long.parseLong(t.i)] })"), "a long is no index");
        assertThrows(IllegalArgumentException.class, () -> tagged("m.tag({ t -> def levels = "
            + "java.util.Arrays.asList(t.names.split(','))\n t.r = levels[t.status.charAt(0)] ?: 'other' })",
            labels("names", "client,server", "status", "503")), "a character is no index");
    }

    @Test
    void regexIndexingFollowsRegexGrammar() throws Exception {
        // m[i][j] is match i, group j (group 0 the whole match) for every pattern; Groovy differs only for a pattern
        // without groups, where m[0] is the matched text and m[0][0] its first character
        assertTrue(generator.compileFilter("{ t -> def m = (t.raw =~ /[a-z]+/); m[0][0] == 'svc' }")
            .test(Map.of("raw", "svc-1")));
        assertTrue(generator.compileFilter("{ t -> def m = (t.raw =~ /[a-z]+/); m[1][0] == 'b' }")
            .test(Map.of("raw", "svc-b")));
        assertTrue(generator.compileFilter("{ t -> (t.raw =~ /api-(.*)/)[0][1] == 'x' }").test(Map.of("raw", "api-x")));
        assertTrue(generator.compileFilter("{ t -> def r = t.kind == 'pod' ? (t.name =~ /^[a-z]+/) : "
            + "(t.name =~ /[a-z]+$/)\n r[0][0] == 'api' }").test(Map.of("kind", "pod", "name", "api-1")));
    }

    @Test
    void aFilterReadsASlashyRegexArgument() throws Exception {
        final MalFilter filter = generator.compileFilter("{ t -> t.a.split(/\\|/)[0] == 'x' }");
        assertTrue(filter.test(Map.of("a", "x|y")));
        assertFalse(filter.test(Map.of("a", "y|x")));
    }

    @Test
    void anInterpolatedArrayPrintsAsGroovyPrintsIt() throws Exception {
        assertEquals("[x, y]", tagged("m.tag({ t -> String[] parts = t.a.split(','); t.r = \"${parts}\" })",
            labels("a", "x,y")).get("r"));
        assertEquals("${t.a}", tagged("m.tag({ t -> t.r = \"\\${t.a}\" })", labels("a", "x")).get("r"),
            "an escaped $ is text");
    }

    @Test
    void labelWritesAndReturnedMaps() throws Exception {
        final Map<String, String> written = tagged("m.tag({ t -> t.n = Double.valueOf('1.5')\n t.b = true\n "
            + "t.put('port', 443) })", labels());
        assertEquals("1.5", written.get("n"));
        assertEquals("true", written.get("b"));
        assertEquals("443", written.get("port"));
        final Map<String, String> chars = tagged("m.tag({ t -> t.c = t.a.charAt(0)\n t.d = t.a.charAt(1) + 'x' })",
            labels("a", "abc"));
        assertEquals("a", chars.get("c"), "a character is written as the one-character string it is");
        assertEquals("bx", chars.get("d"));
        assertEquals("4", tagged("m.tag({ t -> def n = t.a.length(); ['next': n + 1] })", labels("a", "abc"))
            .get("next"), "a returned map's number is written as its string");
        assertThrows(IllegalArgumentException.class, () -> tagged("m.tag({ t -> Object v = ['x']\n t.r = v })",
            labels()), "an object held as Object is refused when written");
        assertEquals(Map.of("svc", "s", "env", "e"), tagged("m.tag({ t -> Map out = ['svc': t.service]\n if (t.env) "
            + "{ out.env = t.env }\n out })", labels("service", "s", "env", "e")));
        final MalFilter mapSize = generator.compileFilter("{ t -> def m = ['a': t.a]; m.size() == 1 }");
        assertTrue(mapSize.test(Map.of("a", "x")));
        assertThrows(IllegalArgumentException.class, () -> generator.compile("t",
            "m.tag({ t -> t.kind ? ['r': t.raw.split('/')] : t })"));
        assertThrows(IllegalArgumentException.class, () -> generator.compile("t", "m.tag({ t -> t.kind ? "
            + "['r': t.raw.split('/')] : ['r': 'fallback'] })"));
        assertThrows(IllegalArgumentException.class, () -> generator.compile("t", "m.tag({ t -> t.put(443, 'https') })"));
    }

    @Test
    void aLocalHoldingTheLabelsReadsThemAndOnlyBuiltMapsAreWritten() throws Exception {
        assertEquals("[env:prod]", tagged("m.tag({ t -> def labels = t\n t.summary = labels.toString() })",
            labels("env", "prod")).get("summary"), "the labels read through a local, printed as Groovy prints a map");
        assertEquals("d", tagged("m.tag({ t -> def other = ['a': 'b']\n other.put('c', 'd')\n t.r = other.get('c') })",
            labels()).get("r"));
        for (final String refused : new String[] {
            // a local that holds the labels, or may: read only
            "m.tag({ t -> def labels = t\n labels.put('x', 1) })",
            "m.tag({ t -> Map out = t.a == '1' ? t : ['a': 'b']\n out.put('x', 1) })",
            "m.tag({ t -> Map out = ['a': 'b']\n out = t\n out.x = 'y' })",
            // a name of the generated code, or of no list class
            "m.tag({ t -> Map raw = _raw\n raw.put('leaked', '1') })",
            // a static method through a value
            "m.tag({ t -> t.r = t.a.valueOf(1) })",
            // a map a branch's own local built is that branch's: the outer one holds the labels
            "m.tag({ t -> Map m = t\n if (t.x == 'never') { Map m = ['a': 'b'] }\n m.put('changed', 'v') })",
            // a name declared twice in one scope, as Groovy refuses
            "m.tag({ t -> Map m = ['a': 'b']\n if (t.x == 'y') { m = t\n Map m = ['c': 'd'] }\n m.put('k', 'v') })",
            "m.tag({ t -> t.r = Math.max(1, 2) })"}) {
            final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> generator.compile("closure_semantics", refused), refused);
            assertTrue(e.getMessage().contains("map the closure built") || e.getMessage().contains("not supported")
                || e.getMessage().contains("already declared"), refused + ": " + e.getMessage());
        }
        assertThrows(IllegalArgumentException.class, () -> generator.compileFilter(
            "{ t -> Map raw = _raw\n raw.put('leaked', '1')\n return true }"));
        assertThrows(IllegalArgumentException.class, () -> generator.compile("t",
            "m.tag({ t -> Map same = t\n same.port = 443 })"));
        // the generated method's argument name is reserved only where that method takes it
        assertEquals("x", tagged("m.tag({ _arg -> _arg.r = 'x' })", labels()).get("r"));
        // a parameter named like a class of the list is the parameter
        assertEquals("abc", tagged("m.tag({ Arrays -> Arrays.r = Arrays.a })", labels("a", "abc")).get("r"));
    }

    @Test
    void aFilterWritesNoLabels() {
        assertThrows(IllegalArgumentException.class, () -> generator.compileFilter("{ t -> t.remove('env') == 'prod' }"),
            "a filter reads the labels");
        for (final String refused : new String[] {
            "{ t -> t.env = 'prod' }", "{ t -> Map current = t\n current.remove('env') == 'prod' }",
            "{ t -> (t).remove('env') == 'prod' }"}) {
            assertThrows(IllegalArgumentException.class, () -> generator.compileFilter(refused), refused);
        }
    }

    @Test
    void onlyTheDocumentedCallsCompile() throws Exception {
        assertEquals("b-c", tagged("m.tag({ t -> t.r = t.s.trim().toLowerCase().replaceAll('/', '-').substring(2) })",
            labels("s", " A/B/C ")).get("r"));
        assertEquals("3", tagged("m.tag({ t -> t.r = String.valueOf(Integer.parseInt(t.a) + Long.parseLong(t.b)) })",
            labels("a", "1", "b", "2")).get("r"));
        assertEquals("2.5", tagged("m.tag({ t -> t.r = String.valueOf(Double.parseDouble(t.a) / 2) })",
            labels("a", "5")).get("r"));
        assertEquals("y", tagged("m.tag({ t -> def parts = Arrays.asList(t.s.split(','))\n "
            + "t.r = parts.contains('x') ? parts.get(1) : 'none' })", labels("s", "x,y")).get("r"));
        for (final String refused : new String[] {
            "Math.round(Double.parseDouble(t.a))", "Math.max(1, 2)", "Math.abs(-1)", "Short.valueOf(t.a)",
            "Byte.parseByte(t.a)", "Float.parseFloat(t.a)", "String.format('%s', t.a)", "t.a.getBytes()",
            "java.time.Instant.now()", "java.util.List.of(t.a)", "Integer.toString(1)"}) {
            final IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> generator.compile("t", "m.tag({ t -> t.r = String.valueOf(" + refused + ") })"), refused);
            assertTrue(e.getMessage().contains("not supported in a MAL closure"), refused + ": " + e.getMessage());
        }
        assertEquals("d", tagged("m.tag({ t -> Map other = ['a': 'b']\n other.put('c', 'd')\n t.r = other.get('c') })",
            labels()).get("r"), "put on a map the closure built");
        assertThrows(IllegalArgumentException.class, () -> generator.compile("t",
            "m.tag({ t -> t.putAll(['a': 'b']) })"), "the labels' put and remove only");
        assertThrows(IllegalArgumentException.class, () -> generator.compile("t",
            "m.service(['svc'], Layer.GENERAL).decorate({ me -> me.attr0 = me.serviceName.bytes.toString() })"));
        assertThrows(IllegalArgumentException.class, () -> generator.compile("t",
            "m.service(['svc'], Layer.GENERAL).decorate({ me -> me.attr0 = me.class.toString() })"));
    }

    @Test
    void documentedCallsRunAsGroovy() throws Exception {
        generator.compile("t", "m.tag({ t -> t.r = String.valueOf(ProcessRegistry.generateVirtualLocalProcess(t.s, "
            + "t.i).startsWith('api')) })");
        generator.compile("t", "m.tag({ t -> def v = ProcessRegistry.generateVirtualLocalProcess(t.service, "
            + "t.instance)\n t.r = v })");
        generator.compile("t", "m.tag({ t -> def name = ProcessRegistry.LOCAL_VIRTUAL_PROCESS\n t.r = name })");
        generator.compile("t", "m.tag({ t -> t.pid = t.process_id ?: ProcessRegistry.LOCAL_VIRTUAL_PROCESS })");
        assertEquals("true", tagged("m.tag({ t -> t.r = String.valueOf(Arrays.asList(t.tags.split(',')).contains('prod')) "
            + "})", labels("tags", "qa,prod")).get("r"), "the receiver after a static call is its result");
        assertEquals("true", tagged("m.tag({ t -> t.r = String.valueOf(Arrays.asList('prod', 'qa').contains(t.env)) })",
            labels("env", "qa")).get("r"), "Arrays.asList packs its arguments");
        assertEquals("true", tagged("m.tag({ t -> t.r = String.valueOf(Arrays.asList(t.getOrDefault('role', "
            + "t.ids.split(','))).contains('a')) })", labels("ids", "a,b")).get("r"), "a single array is the varargs");
        assertEquals("2/prod", tagged("m.tag({ t -> t.r = Arrays.asList('prod', 'qa').size() + '/' "
            + "+ Arrays.asList('prod', 'qa').get(0) })", labels()).get("r"));
        assertEquals("3", tagged("m.tag({ t -> t.r = String.valueOf(t.path.split('/').size()) })",
            labels("path", "a/b/c")).get("r"), "an array's size(), as Groovy gives it");
        assertEquals("true", tagged("m.tag({ t -> t.r = String.valueOf(t.a.length().equals(3)) })", labels("a", "abc"))
            .get("r"), "a primitive receiver is its box");
    }

    @Test
    void argumentsConvertToTheirParameters() throws Exception {
        assertEquals("cd", tagged("m.tag({ t -> t.r = t.s.substring(Integer.valueOf(t.i)) })", labels("s", "abcd", "i", "2"))
            .get("r"), "a wrapper unboxed into an int parameter");
        assertEquals("443", tagged("m.tag({ t -> t.r = t.getOrDefault('port', 443) })", labels()).get("r"),
            "a primitive boxed into an Object parameter");
    }

    @Test
    void overloadDispatchOnRuntimeKinds() throws Exception {
        assertEquals("0", tagged("m.tag({ t -> t.r = String.valueOf(t.flag ? 0 : Double.parseDouble(t.load)) })",
            labels("flag", "y", "load", "1.5")).get("r"));
        assertEquals("443", tagged("m.tag({ t -> t.r = String.valueOf(t.p ?: 443) })", labels()).get("r"),
            "overloads alike but for the parameter");
        assertEquals("443", tagged("m.tag({ t -> Object fallback = '443'\n def port = t.getOrDefault('port', "
            + "fallback)\n t.r = String.valueOf(port) })", labels()).get("r"));
        assertEquals("x", tagged("m.tag({ t -> def v = t.v\n t.r = String.valueOf(v) })", labels("v", "x")).get("r"));
        assertEquals("2.5", tagged("m.tag({ t -> def q = Long.parseLong(t.n) / 4\n t.r = String.valueOf(q ?: 1) })",
            labels("n", "10")).get("r"), "a quotient goes to the Object overload, the nearest to Groovy");
        assertEquals("true", tagged("m.tag({ t -> def p = null\n if (t.a) { p = t.a.trim() }\n "
            + "t.r = String.valueOf(t.name.startsWith(p)) })", labels("a", "or", "name", "orders")).get("r"),
            "a value known only at run time into the one signature");
        final String runTime = "is known only at run time";
        rejects(runTime, () -> tagged("m.tag({ t -> def scaled = Long.parseLong(t.s)\n "
            + "scaled = scaled * 1000.0\n t.r = String.valueOf(t.s.indexOf(scaled)) })", labels("s", "1700000000")));
        rejects(runTime, () -> tagged("m.tag({ t -> def ns = Long.parseLong(t.ts)\n "
            + "if (t.seconds) { ns = ns * 1000000000.0 }\n t.r = String.valueOf(t.ts.indexOf(ns)) })",
            labels("ts", "1700000000000000001")));
        // a kind the argument takes picks another overload than its other kind does
        rejects(runTime, () -> generator.compile("t", "m.tag({ t -> t.r = String.valueOf(t.path.indexOf(t.sep ?: 47)) })"));
        rejects(runTime, () -> generator.compile("t", "m.tag({ t -> t.r = String.valueOf(Integer.valueOf(t.a ?: 1)) })"));
        // a kind the argument takes fits no overload
        rejects(runTime, () -> generator.compile("t",
            "m.tag({ t -> t.r = String.valueOf(t.name.startsWith(t.prefix ?: 404)) })"));
        rejects(runTime, () -> generator.compile("t", "m.tag({ t -> t.r = String.valueOf(Integer.valueOf(t.f ? "
            + "Double.parseDouble(t.f) : Integer.parseInt(t.i))) })"));
        rejects(runTime, () -> generator.compile("t", "m.tag({ t -> t.r = String.valueOf(Long.valueOf(t.mult ? "
            + "Double.parseDouble(t.mult) : Long.parseLong(t.ns))) })"));
        rejects(runTime, () -> generator.compile("t", "m.tag({ t -> t.r = t.s.substring(Integer.parseInt(t.a) ?: "
            + "Long.parseLong(t.b)) })"));
        rejects(runTime, () -> generator.compile("t", "m.tag({ t -> t.r = String.valueOf(Integer.valueOf("
            + "Long.parseLong(t.n) / 4)) })"));
    }

    @Test
    void anInterfaceValueAnswersObjectsMethods() throws Exception {
        assertEquals("[a, b]", tagged("m.tag({ t -> t.r = t.keySet().toString() })", labels("a", "1", "b", "2"))
            .get("r"));
        assertEquals("a]", tagged("m.tag({ t -> t.r = Arrays.asList('a').toString().substring(Integer.valueOf('1')) })",
            labels()).get("r"), "typed String, so the wrapper is unboxed");
    }

    @Test
    void aRefusedCallStopsTheBootNamingTheRule() {
        final IllegalStateException e = assertThrows(IllegalStateException.class, () -> DSL.parse("m_r",
            "m.tag({ t -> t.r = String.valueOf(Math.max(1, 2)) })"));
        assertTrue(e.getMessage().contains("m_r") && e.getMessage().contains("not supported in a MAL closure"),
            e.getMessage());
        final IllegalStateException f = assertThrows(IllegalStateException.class,
            () -> new FilterExpression("{ t -> t.job.getBytes() != null }"));
        assertTrue(f.getMessage().contains("not supported in a MAL closure"), f.getMessage());
        final IllegalStateException g = assertThrows(IllegalStateException.class,
            () -> new FilterExpression("{ t -> t.job.getBytes() != null }", "filter",
                DslSourceRef.ofRule("otel-rules/check.yaml", 41), null, null));
        assertTrue(g.getMessage().contains("otel-rules/check.yaml") && g.getMessage().contains("41"), g.getMessage());
    }

    @Test
    void aShippedFilterKeepsItsLineTable() throws Exception {
        final MalFilter filter = generator.compileFilter(
            "{ tags -> tags.job_name == 'aws-cloud-eks-monitoring' && tags.Service?.trim() }");
        final Map<String, Object> bad = new HashMap<>();
        bad.put("job_name", "aws-cloud-eks-monitoring");
        bad.put("Service", 42);
        final Throwable thrown = assertThrows(Throwable.class, () -> filter.test((Map) bad));
        final StackTraceElement frame = Arrays.stream(thrown.getStackTrace())
            .filter(e -> e.getClassName().equals(filter.getClass().getName())).findFirst().orElseThrow();
        assertTrue(frame.getLineNumber() > 0, "the generated filter reports its line: " + frame);
    }

    @Test
    void typeInferenceStaysLinearInTheExpressionSize() throws Exception {
        final MalFilter sum = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> generator.compileFilter(
            "{ t -> Integer.parseInt(t.a1) + Integer.parseInt(t.a2) + Integer.parseInt(t.a3) + Integer.parseInt(t.a4) + Integer.parseInt(t.a5) + Integer.parseInt(t.a6) + Integer.parseInt(t.a7) + Integer.parseInt(t.a8) + Integer.parseInt(t.a9) + Integer.parseInt(t.a10) + Integer.parseInt(t.a11) + Integer.parseInt(t.a12) + Integer.parseInt(t.a13) + Integer.parseInt(t.a14) + Integer.parseInt(t.a15) + Integer.parseInt(t.a16) + Integer.parseInt(t.a17) + Integer.parseInt(t.a18) + Integer.parseInt(t.a19) > 0 }"), "type inference stays linear in the expression size");
        final Map<String, String> ones = new HashMap<>();
        for (int i = 1; i <= 19; i++) {
            ones.put("a" + i, "1");
        }
        assertTrue(sum.test(ones));
    }

    @Test
    void aLocalNamedLikeTheParameterBindingKeepsTheTemporariesFirst() throws Exception {
        assertTrue(generator.compileFilter("{ t -> boolean is_raw = (t.m ?: 'x') == 'raw'; boolean k = is_raw; k }")
            .test(Map.of("m", "raw")), "a local named like the parameter binding does not move the temporaries");
    }

    private Map<String, String> tagged(final String expression, final Map<String, String> labels) throws Exception {
        final MalExpression expr = generator.compile("closure_semantics", expression);
        final SampleFamily input = SampleFamilyBuilder.newBuilder(
            Sample.builder().name("m").labels(ImmutableMap.copyOf(labels)).value(1.0).build()).build();
        return expr.run(Map.of("m", input)).samples[0].getLabels();
    }

    /**
     * Runs a decorate closure on the service of one sample.
     */
    private void decorated(final String closure) throws Exception {
        final MalExpression expr = generator.compile("closure_semantics",
            "m.service(['svc'], Layer.GENERAL).decorate(" + closure + ")");
        final SampleFamily input = SampleFamilyBuilder.newBuilder(
            Sample.builder().name("m").labels(ImmutableMap.of("svc", "s")).value(1.0).build()).build();
        MeterEntity.setNamingControl(new NamingControl(512, 512, 512, new EndpointNameGrouping()));
        try {
            expr.run(Map.of("m", input));
        } finally {
            MeterEntity.setNamingControl(null);
        }
    }

    @SuppressWarnings("unchecked")
    private Function<Map<String, String>, Map<String, String>> instanceProperties(final String expression)
        throws Exception {
        final MalExpression expr = generator.compile("closure_semantics", expression);
        for (final Field field : expr.getClass().getDeclaredFields()) {
            if (Function.class.isAssignableFrom(field.getType()) || field.getName().contains("instance")) {
                field.setAccessible(true);
                final Object closure = field.get(Modifier.isStatic(field.getModifiers()) ? null : expr);
                if (closure instanceof Function) {
                    return (Function<Map<String, String>, Map<String, String>>) closure;
                }
            }
        }
        throw new AssertionError("no properties closure in " + expr.getClass().getName());
    }

    private static Map<String, String> labels(final String... keyValues) {
        final Map<String, String> labels = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            labels.put(keyValues[i], keyValues[i + 1]);
        }
        return labels;
    }
}
