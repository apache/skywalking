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

package org.apache.skywalking.oap.trace.analysis.checker;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.RefType;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;

/**
 * Segments built for the branches the rules port, each named after what it exercises. Run in this order, one
 * multi-segment run per profile, so the stateful collaborators carry from one segment to the next as in the OAP.
 */
final class CheckerFixtures {
    private static final long START = 1_700_000_000_000L;

    private CheckerFixtures() {
    }

    /**
     * One segment, the rules that must produce an event on it when they run (its positive rules), and the documented
     * difference it shows, if any.
     */
    @Getter
    static final class CheckerFixture {
        private final String name;
        private final SegmentObject segment;
        private final Set<String> positiveFor = new LinkedHashSet<>();
        private final List<String[]> twinMapping = new ArrayList<>();
        private CheckerDifference difference;
        /**
         * A segment the Java analysis handles as the rules handle this one, so v1 on it, after {@link #twinMapping},
         * is what v2 must do on this one; null when the difference's check needs none.
         */
        private SegmentObject twin;

        CheckerFixture(final String name, final SegmentObject segment) {
            this.name = name;
            this.segment = segment;
        }

        CheckerFixture positive(final String... rules) {
            positiveFor.addAll(Arrays.asList(rules));
            return this;
        }

        CheckerFixture differs(final CheckerDifference difference) {
            this.difference = difference;
            return this;
        }

        /**
         * @param twin    the segment v1 handles as the rules handle this one
         * @param mapping literal replacements, in order, that turn v1's events on the twin into v2's on this one
         */
        CheckerFixture twin(final SegmentObject twin, final String... mapping) {
            this.twin = twin;
            // The stored segment is the one received, so the twin's bytes stand for this fixture's.
            twinMapping.add(new String[] {base64(twin.toByteArray()), base64(segment.toByteArray())});
            for (int i = 0; i + 1 < mapping.length; i += 2) {
                twinMapping.add(new String[] {mapping[i], mapping[i + 1]});
            }
            return this;
        }

        /**
         * @return v1's events on the twin, mapped to what v2 must do on this fixture
         */
        List<String> mapTwin(final List<String> twinEvents) {
            final List<String> mapped = new ArrayList<>(twinEvents.size());
            for (final String event : twinEvents) {
                String e = event;
                for (final String[] replacement : twinMapping) {
                    e = e.replace(replacement[0], replacement[1]);
                }
                mapped.add(e);
            }
            return mapped;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /**
     * An address 80 characters long, past the default service name limit of 70.
     */
    static final String LONG_GATEWAY = "gateway-with-a-very-long-host-name-past-the-service-name-limit.example.com:8080";
    static final String BARE_GATEWAY = "bare-gw";
    static final String NO_INSTANCE_ALIAS = "noinst:80";
    static final String MALFORMED_ALIAS = "malformed:80";
    static final String VIRTUAL_ALIAS = "virtual:80";
    /**
     * An alias row loaded halfway through the warm pass, after the reads before it changed the cache's eviction order.
     */
    static final String LATE_ALIAS = "late:80";

    static List<CheckerFixture> all() {
        final List<CheckerFixture> all = new ArrayList<>();
        all.addAll(basics());
        all.addAll(spanShapes());
        all.addAll(statusAndDatabase());
        all.addAll(cacheAndMq());
        all.addAll(logicEndpoints());
        all.addAll(segmentValues());
        all.addAll(genAI());
        all.addAll(aliasesAndGateways());
        return Collections.unmodifiableList(all);
    }

    private static List<CheckerFixture> basics() {
        return List.of(
            fixture("http-entry", segment("svc-a", "inst-a", "trace-1", "seg-1",
                entry(0, -1, "/users", SpanLayer.Http, 1)
                    .addAllTags(tags("http.method", "GET", "status_code", "200"))))
                .positive("native-rpc-entry", "native-segment"),
            fixture("rpc-entry-exit-local", segment("orders", "orders-1", "trace-2", "seg-2",
                entry(0, -1, "/orders", SpanLayer.Http, 1)
                    .addRefs(ref(RefType.CrossProcess, "trace-2", "seg-p", 1, "frontend", "frontend-1", "/home",
                        "orders:8080")),
                exit(1, 0, "/stock", SpanLayer.Http, 2, "inventory:8080").setIsError(true)
                    .addAllTags(tags("status_code", "500")),
                local(2, 0, "compute")))
                .positive("native-rpc-exit", "native-network-alias"),
            fixture("database", segment("orders", "orders-1", "trace-3", "seg-3",
                entry(0, -1, "/orders/1", SpanLayer.Http, 1),
                exit(1, 0, "Mysql/JDBC/PreparedStatement/executeQuery", SpanLayer.Database, 33, "mysql:3306")
                    .setEndTime(START + 301)
                    .addAllTags(tags("db.type", "Mysql", "db.instance", "shop",
                        "db.statement", "select * from orders where id = ?"))))
                .positive("native-virtual-database"),
            fixture("cache", segment("orders", "orders-1", "trace-4", "seg-4",
                entry(0, -1, "/cart", SpanLayer.Http, 1),
                exit(1, 0, "Jedis/get", SpanLayer.Cache, 30, "redis:6379").setEndTime(START + 51)
                    .addAllTags(tags("cache.type", "Redis", "cache.cmd", "GET", "cache.key", "k1",
                        "cache.op", "read"))))
                .positive("native-virtual-cache"),
            fixture("mq-producer", segment("orders", "orders-1", "trace-5", "seg-5",
                entry(0, -1, "/checkout", SpanLayer.Http, 1),
                exit(1, 0, "Kafka/orders/Producer", SpanLayer.MQ, 40, "kafka:9092")
                    .addAllTags(tags("mq.topic", "orders", "mq.broker", "kafka:9092"))))
                .positive("native-virtual-mq"),
            fixture("mq-consumer", segment("shipping", "shipping-1", "trace-5", "seg-6",
                entry(0, -1, "Kafka/orders/Consumer/group-1", SpanLayer.MQ, 41)
                    .addRefs(ref(RefType.CrossProcess, "trace-5", "seg-5", 1, "orders", "orders-1", "/checkout",
                        "kafka:9092"))
                    .addAllTags(tags("mq.topic", "orders", "mq.broker", "kafka:9092", "transmission.latency", "15"))))
                .positive("native-virtual-mq"),
            fixture("cross-thread", segment("orders", "orders-1", "trace-6", "seg-7",
                local(0, -1, "async-task")
                    .addRefs(ref(RefType.CrossThread, "trace-6", "seg-8", 2, "orders", "orders-1", "/start", "")),
                exit(1, 0, "/notify", SpanLayer.Http, 2, "notifier:8080")))
                .positive("native-endpoint-cross-thread"),
            fixture("cross-thread-without-parent-endpoint-error", segment("orders", "orders-1", "trace-6a", "seg-7a",
                local(0, -1, "failing-task").setIsError(true)
                    .addRefs(ref(RefType.CrossThread, "trace-6a", "seg-8a", 2, "orders", "orders-1", "", "")))),
            fixture("cross-thread-mq-local", segment("orders", "orders-1", "trace-6b", "seg-7b",
                local(0, -1, "mq-handler").setSpanLayer(SpanLayer.MQ)
                    .addRefs(ref(RefType.CrossProcess, "trace-6b", "seg-8b", 1, "producer", "producer-1", "/send",
                        "kafka:9092")))),
            fixture("cross-thread-via-gateway", segment("orders", "orders-1", "trace-6c", "seg-7c",
                local(0, -1, "gateway-task")
                    .addRefs(ref(RefType.CrossProcess, "trace-6c", "seg-8c", 1, "frontend", "frontend-1", "/home",
                        "gateway.local:8080")))),
            fixture("mq-entry-without-references", segment("shipping", "shipping-1", "trace-5a", "seg-6a",
                entry(0, -1, "Kafka/orders/Consumer/group-2", SpanLayer.MQ, 41).setPeer("kafka:9092")
                    .addAllTags(tags("mq.topic", "orders")))),
            fixture("mq-entry-without-references-or-peer", segment("shipping", "shipping-1", "trace-5b", "seg-6b",
                entry(0, -1, "Kafka/orders/Consumer/group-3", SpanLayer.MQ, 41)
                    .addAllTags(tags("mq.topic", "orders")))),
            fixture("exit-rpc-framework", segment("orders", "orders-1", "trace-6e", "seg-7e",
                entry(0, -1, "/grpc-caller", SpanLayer.Http, 1),
                exit(1, 0, "grpc.Stock/Reserve", SpanLayer.RPCFramework, 23, "stock:50051")
                    .addAllTags(tags("rpc.status_code", "OK")))),
            fixture("exit-unknown-layer", segment("orders", "orders-1", "trace-6d", "seg-7d",
                entry(0, -1, "/unknown-exit", SpanLayer.Http, 1),
                exit(1, 0, "/somewhere", SpanLayer.Unknown, 2, "unknown-peer:80"))),
            fixture("logic-endpoint", segment("orders", "orders-1", "trace-7", "seg-9",
                entry(0, -1, "/pay", SpanLayer.Http, 1),
                local(1, 0, "checkout-logic").addAllTags(tags("x-le",
                    "{\"name\":\"checkout\",\"latency\":30,\"status\":true,\"logic-span\":true}"))))
                .positive("native-rpc-logic-endpoint"),
            fixture("genai", segment("assistant", "assistant-1", "trace-8", "seg-10",
                entry(0, -1, "/ask", SpanLayer.Http, 1),
                exit(1, 0, "chat gpt-4o", SpanLayer.GenAI, 0, "api.openai.com:443").setEndTime(START + 801)
                    .addAllTags(tags("gen_ai.response.model", "gpt-4o", "gen_ai.provider.name", "openai",
                        "gen_ai.usage.input_tokens", "10", "gen_ai.usage.output_tokens", "20"))))
                .positive("native-virtual-genai", "native-ai-evaluation"),
            fixture("skip-and-unknown", segment("orders", "orders-1", "trace-9", "seg-11",
                entry(0, -1, "/health", SpanLayer.Http, 1).setSkipAnalysis(true),
                exit(1, 0, "/odd", SpanLayer.Http, 2, "odd:80").setSpanTypeValue(7),
                exit(2, 0, "/skip", SpanLayer.Http, 2, "skip:80").setSkipAnalysis(true))),
            fixture("no-span-zero", segment("orders", "orders-1", "trace-10", "seg-12",
                exit(1, 0, "/a", SpanLayer.Http, 2, "a:80"),
                local(2, 1, "b"))),
            fixture("empty-segment", SegmentObject.newBuilder().setService("orders").setServiceInstance("orders-1")
                                                  .setTraceId("trace-11").setTraceSegmentId("seg-13").build()),
            fixture("late-span-zero-and-errors", segment("orders", "orders-1", "trace-12", "seg-14",
                exit(1, 0, "/x", SpanLayer.Http, 2, "x:80").addAllTags(tags("status_code", "abc")),
                entry(0, -1, "/late", SpanLayer.Http, 1).setIsError(true))),
            fixture("gateway-peer", segment("orders", "orders-1", "trace-13", "seg-15",
                entry(0, -1, "/via-gateway", SpanLayer.Http, 1)
                    .addRefs(ref(RefType.CrossProcess, "trace-13", "seg-q", 1, "frontend", "frontend-1", "/home",
                        "gateway.local:8080")),
                exit(1, 0, "/api", SpanLayer.Http, 2, "gateway.local:8080")))
        );
    }

    private static List<CheckerFixture> spanShapes() {
        return List.of(
            fixture("span-zero-exit", segment("orders", "orders-1", "trace-20", "seg-20",
                exit(0, -1, "/first-exit", SpanLayer.Http, 2, "pricing:8080"),
                local(1, 0, "after"))),
            fixture("span-zero-local", segment("orders", "orders-1", "trace-21", "seg-21",
                local(0, -1, "scheduled-job"),
                exit(1, 0, "/report", SpanLayer.Http, 2, "reports:8080"))),
            fixture("span-zero-unknown-type", segment("orders", "orders-1", "trace-22", "seg-22",
                entry(0, -1, "/odd-root", SpanLayer.Http, 1).setSpanTypeValue(9),
                exit(1, 0, "/next", SpanLayer.Http, 2, "next:80"))),
            fixture("repeated-span-zero", segment("orders", "orders-1", "trace-23", "seg-23",
                entry(0, -1, "/one", SpanLayer.Http, 1),
                local(0, -1, "two"),
                entry(0, -1, "/three", SpanLayer.Http, 1))),
            fixture("unknown-span-layer", segment("orders", "orders-1", "trace-24", "seg-24",
                entry(0, -1, "/layerless", SpanLayer.Http, 1).setSpanLayerValue(99),
                exit(1, 0, "/somewhere", SpanLayer.Http, 2, "somewhere:80").setSpanLayerValue(99))),
            fixture("reverse-wire-order", segment("orders", "orders-1", "trace-25", "seg-25",
                exit(2, 1, "/deep", SpanLayer.Http, 2, "deep:80"),
                local(1, 0, "middle"),
                entry(0, -1, "/top", SpanLayer.Http, 1))),
            fixture("faas-layer", segment("fn", "fn-1", "trace-26", "seg-26",
                entry(0, -1, "/invoke", SpanLayer.FAAS, 1)
                    .addRefs(ref(RefType.CrossProcess, "trace-26", "seg-p26", 1, "caller", "caller-1", "/call",
                        "fn:443")),
                exit(1, 0, "/downstream", SpanLayer.FAAS, 2, "downstream:80"),
                exit(2, 0, "/plain-http", SpanLayer.Http, 2, "plain:80"))),
            fixture("skip-local-logic-endpoint", segment("orders", "orders-1", "trace-27", "seg-27",
                entry(0, -1, "/skip-logic", SpanLayer.Http, 1),
                local(1, 0, "skipped-logic").setSkipAnalysis(true).addAllTags(tags("x-le",
                    "{\"name\":\"skipped\",\"latency\":5,\"status\":true,\"logic-span\":true}")))),
            fixture("skip-virtual-database", segment("orders", "orders-1", "trace-28", "seg-28",
                entry(0, -1, "/skip-db", SpanLayer.Http, 1),
                exit(1, 0, "Mysql/skip", SpanLayer.Database, 33, "mysql:3306").setSkipAnalysis(true)
                    .setEndTime(START + 900).addAllTags(tags("db.type", "Mysql", "db.statement", "select 1")))),
            fixture("several-references", segment("billing", "billing-1", "trace-29", "seg-29",
                entry(0, -1, "/bill", SpanLayer.Http, 1)
                    .addRefs(ref(RefType.CrossProcess, "trace-29", "seg-a", 1, "frontend", "frontend-1", "/a",
                        "billing:8080"))
                    .addRefs(ref(RefType.CrossThread, "trace-29", "seg-b", 2, "billing", "billing-1", "/b", ""))
                    .addRefs(ref(RefType.CrossProcess, "trace-29", "seg-c", 3, "admin", "admin-1", "/c",
                        "billing:9090")),
                local(1, 0, "fan-in")
                    .addRefs(ref(RefType.CrossThread, "trace-29", "seg-d", 4, "billing", "billing-1", "/d", ""))
                    .addRefs(ref(RefType.CrossProcess, "trace-29", "seg-e", 5, "batch", "batch-1", "/e",
                        "billing:7070")))),
            fixture("mq-entry-blank-first-reference", segment("shipping", "shipping-1", "trace-30", "seg-30",
                entry(0, -1, "Kafka/events/Consumer/g", SpanLayer.MQ, 41)
                    .addRefs(ref(RefType.CrossProcess, "trace-30", "seg-p", 1, "orders", "orders-1", "/x", ""))
                    .addRefs(ref(RefType.CrossProcess, "trace-30", "seg-q", 1, "orders", "orders-1", "/y",
                        "kafka:9092"))
                    .addAllTags(tags("mq.topic", "events", "mq.broker", "kafka:9092")))),
            fixture("whitespace-names", segment(" ", "  ", "trace-31", "seg-31",
                entry(0, -1, " ", SpanLayer.Http, 1)
                    .addRefs(ref(RefType.CrossProcess, "trace-31", "seg-p", 1, " ", " ", " ", " ")),
                exit(1, 0, " ", SpanLayer.Http, 2, " "))),
            fixture("empty-names", segment("orders", "orders-1", "trace-32", "seg-32",
                entry(0, -1, "", SpanLayer.Http, 1)
                    .addRefs(ref(RefType.CrossProcess, "trace-32", "seg-p", 1, "", "", "", "")),
                exit(1, 0, "", SpanLayer.Http, 2, ""))),
            fixture("relation-component-a", segment("orders", "orders-1", "trace-33", "seg-33",
                entry(0, -1, "/shared", SpanLayer.Http, 1)
                    .addRefs(ref(RefType.CrossProcess, "trace-33", "seg-p", 1, "frontend", "frontend-1", "/home",
                        "orders:8080")))),
            fixture("relation-component-b", segment("orders", "orders-1", "trace-34", "seg-34",
                entry(0, -1, "/shared", SpanLayer.Http, 14)
                    .addRefs(ref(RefType.CrossProcess, "trace-34", "seg-p", 1, "frontend", "frontend-1", "/home",
                        "orders:8080")))),
            // The same entity and minute with the component ids in the other order.
            fixture("relation-component-b-first", segment("payments", "payments-1", "trace-37", "seg-37",
                entry(0, -1, "/shared", SpanLayer.Http, 14)
                    .addRefs(ref(RefType.CrossProcess, "trace-37", "seg-p", 1, "frontend", "frontend-1", "/home",
                        "payments:8080")))),
            fixture("relation-component-a-second", segment("payments", "payments-1", "trace-38", "seg-38",
                entry(0, -1, "/shared", SpanLayer.Http, 1)
                    .addRefs(ref(RefType.CrossProcess, "trace-38", "seg-p", 1, "frontend", "frontend-1", "/home",
                        "payments:8080")))),
            fixture("grouping-capacity", segment("catalog", "catalog-1", "trace-35", "seg-35",
                entry(0, -1, "/items/1", SpanLayer.Http, 1),
                entry(1, 0, "/items/2", SpanLayer.Http, 1),
                entry(2, 0, "/items/3", SpanLayer.Http, 1),
                entry(3, 0, "/items/4", SpanLayer.Http, 1),
                entry(4, 0, "/items/5", SpanLayer.Http, 1),
                entry(5, 0, "/items/6", SpanLayer.Http, 1),
                entry(6, 0, "/search/a", SpanLayer.Http, 1),
                entry(7, 0, "/search/b", SpanLayer.Http, 1),
                entry(8, 0, "/search/c", SpanLayer.Http, 1),
                entry(9, 0, "/search/d", SpanLayer.Http, 1),
                entry(10, 0, "/search/e", SpanLayer.Http, 1))),
            fixture("repeated-tag-key", segment("orders", "orders-1", "trace-36", "seg-36",
                entry(0, -1, "/tagged", SpanLayer.Http, 1)
                    .addAllTags(tags("region", "first", "region", "second", "http.method", "GET"))))
                .differs(CheckerDifference.REPEATED_TAG_FIRST_VALUE)
        );
    }

    private static List<CheckerFixture> statusAndDatabase() {
        return List.of(
            fixture("status-valid-invalid-valid", segment("orders", "orders-1", "trace-40", "seg-40",
                entry(0, -1, "/status", SpanLayer.Http, 1)
                    .addAllTags(tags("status_code", "200", "http.status_code", "abc", "status_code", "404"))))
                .differs(CheckerDifference.REPEATED_TAG_FIRST_VALUE),
            fixture("repeated-rpc-status", segment("orders", "orders-1", "trace-41", "seg-41",
                entry(0, -1, "grpc.Service/Call", SpanLayer.RPCFramework, 23)
                    .addAllTags(tags("rpc.status_code", "OK", "rpc.status_code", "CANCELLED"))))
                .differs(CheckerDifference.REPEATED_TAG_FIRST_VALUE),
            fixture("database-tags-reordered-at-threshold", segment("orders", "orders-1", "trace-42", "seg-42",
                entry(0, -1, "/db-order", SpanLayer.Http, 1),
                exit(1, 0, "Mysql/query", SpanLayer.Database, 33, "mysql:3306").setEndTime(START + 201)
                    .addAllTags(tags("db.statement", "update t set a = 1", "db.instance", "shop", "db.type",
                        "Mysql")))),
            fixture("database-low-threshold-type-first", segment("orders", "orders-1", "trace-43", "seg-43",
                entry(0, -1, "/mongo", SpanLayer.Http, 1),
                exit(1, 0, "MongoDB/find", SpanLayer.Database, 42, "mongo:27017").setEndTime(START + 151)
                    .addAllTags(tags("db.type", "MongoDB", "db.type", "Mysql", "db.statement", "find orders")))),
            fixture("database-blank-final-statement", segment("orders", "orders-1", "trace-44", "seg-44",
                entry(0, -1, "/blank-sql", SpanLayer.Http, 1),
                exit(1, 0, "Mysql/blank", SpanLayer.Database, 33, "mysql:3306").setEndTime(START + 601)
                    .addAllTags(tags("db.type", "Mysql", "db.statement", "select 2", "db.statement", "  ")))),
            fixture("database-uppercase-i-type", segment("orders", "orders-1", "trace-45", "seg-45",
                entry(0, -1, "/influx", SpanLayer.Http, 1),
                exit(1, 0, "Influx/query", SpanLayer.Database, 33, "influx:8086").setEndTime(START + 401)
                    .addAllTags(tags("db.type", "INFLUXDB", "db.statement", "SELECT * FROM cpu"))))
        );
    }

    private static List<CheckerFixture> cacheAndMq() {
        return List.of(
            fixture("cache-repeated-key", segment("orders", "orders-1", "trace-50", "seg-50",
                entry(0, -1, "/cache-dup", SpanLayer.Http, 1),
                exit(1, 0, "Jedis/mget", SpanLayer.Cache, 30, "redis:6379").setEndTime(START + 31)
                    .addAllTags(tags("cache.type", "Redis", "cache.cmd", "MGET", "cache.key", "k1", "cache.key",
                        "k2", "cache.op", "read"))))
                .differs(CheckerDifference.REPEATED_CACHE_TAG_ANALYZED)
                .twin(segment("orders", "orders-1", "trace-50", "seg-50",
                    entry(0, -1, "/cache-dup", SpanLayer.Http, 1),
                    exit(1, 0, "Jedis/mget", SpanLayer.Cache, 30, "redis:6379").setEndTime(START + 31)
                        .addAllTags(tags("cache.type", "Redis", "cache.cmd", "MGET", "cache.key", "k1",
                            "cache.key.second", "k2", "cache.op", "read"))),
                    // The renamed key is the repeated one on v2, whose maps keep the first value only.
                    ", \"cache.key.second\"=\"k2\"", "", "cache.key.second", "cache.key"),
            fixture("cache-uppercase-type-read", segment("orders", "orders-1", "trace-61", "seg-61",
                entry(0, -1, "/cache-upper", SpanLayer.Http, 1),
                exit(1, 0, "Jedis/get", SpanLayer.Cache, 30, "redis:6379").setEndTime(START + 16)
                    .addAllTags(tags("cache.type", "REDIS", "cache.cmd", "GET", "cache.key", "k", "cache.op",
                        "read")))),
            fixture("cache-missing-type", segment("orders", "orders-1", "trace-51", "seg-51",
                entry(0, -1, "/cache-untyped", SpanLayer.Http, 1),
                exit(1, 0, "Cache/get", SpanLayer.Cache, 30, "memcached:11211")
                    .addAllTags(tags("cache.cmd", "GET", "cache.key", "k", "cache.op", "read")))),
            fixture("cache-blank-type", segment("orders", "orders-1", "trace-52", "seg-52",
                entry(0, -1, "/cache-blank", SpanLayer.Http, 1),
                exit(1, 0, "Cache/set", SpanLayer.Cache, 30, "memcached:11211")
                    .addAllTags(tags("cache.type", "  ", "cache.op", "write")))),
            fixture("cache-without-command-or-key", segment("orders", "orders-1", "trace-53", "seg-53",
                entry(0, -1, "/cache-bare", SpanLayer.Http, 1),
                exit(1, 0, "Jedis/set", SpanLayer.Cache, 30, "redis:6379").setEndTime(START + 41)
                    .addAllTags(tags("cache.type", "Redis", "cache.op", "write")))),
            fixture("cache-unknown-and-mixed-case-operation", segment("orders", "orders-1", "trace-54", "seg-54",
                entry(0, -1, "/cache-ops", SpanLayer.Http, 1),
                exit(1, 0, "Jedis/del", SpanLayer.Cache, 30, "redis:6379")
                    .addAllTags(tags("cache.type", "REDIS", "cache.cmd", "DEL", "cache.key", "k", "cache.op",
                        "Delete")),
                exit(2, 0, "Jedis/get", SpanLayer.Cache, 30, "redis:6379").setEndTime(START + 62)
                    .addAllTags(tags("cache.type", "redis", "cache.cmd", "GET", "cache.op", "READ")))),
            fixture("mq-latency-blank", mqConsumer("trace-55", "seg-55", "")),
            fixture("mq-latency-signed", mqConsumer("trace-56", "seg-56", "-5")),
            fixture("mq-latency-padded", mqConsumer("trace-57", "seg-57", " 7 ")),
            fixture("mq-latency-overflowing", mqConsumer("trace-58", "seg-58", "99999999999999999999")),
            fixture("mq-latency-invalid-before-valid", segment("shipping", "shipping-1", "trace-59", "seg-59",
                entry(0, -1, "Kafka/orders/Consumer/group-1", SpanLayer.MQ, 41)
                    .addRefs(ref(RefType.CrossProcess, "trace-59", "seg-5", 1, "orders", "orders-1", "/checkout",
                        "kafka:9092"))
                    .addAllTags(tags("mq.topic", "orders", "mq.broker", "kafka:9092", "transmission.latency", "abc",
                        "transmission.latency", "15")))),
            fixture("cache-local-span", segment("orders", "orders-1", "trace-62", "seg-62",
                entry(0, -1, "/cache-local", SpanLayer.Http, 1),
                local(1, 0, "Caffeine/get").setSpanLayer(SpanLayer.Cache).setComponentId(30)
                    .addAllTags(tags("cache.type", "Caffeine", "cache.cmd", "get", "cache.key", "k", "cache.op",
                        "read")))),
            fixture("cache-slow-write-error", segment("orders", "orders-1", "trace-63", "seg-63",
                entry(0, -1, "/cache-write", SpanLayer.Http, 1),
                exit(1, 0, "Jedis/set", SpanLayer.Cache, 30, "redis:6379").setEndTime(START + 31).setIsError(true)
                    .addAllTags(tags("cache.type", "Redis", "cache.cmd", "SET", "cache.key", "k", "cache.op",
                        "write")))),
            fixture("cache-fast-write", segment("orders", "orders-1", "trace-67", "seg-67",
                entry(0, -1, "/cache-fast-write", SpanLayer.Http, 1),
                exit(1, 0, "Jedis/set", SpanLayer.Cache, 30, "redis:6379").setEndTime(START + 6)
                    .addAllTags(tags("cache.type", "Redis", "cache.cmd", "SET", "cache.key", "k", "cache.op",
                        "write")))),
            fixture("mq-error-with-topic", segment("orders", "orders-1", "trace-68", "seg-68",
                entry(0, -1, "/mq-error-topic", SpanLayer.Http, 1),
                exit(1, 0, "Kafka/orders/Producer", SpanLayer.MQ, 40, "kafka:9092").setIsError(true)
                    .addAllTags(tags("mq.topic", "orders")))),
            fixture("database-error", segment("orders", "orders-1", "trace-64", "seg-64",
                entry(0, -1, "/db-error", SpanLayer.Http, 1),
                exit(1, 0, "Mysql/execute", SpanLayer.Database, 33, "mysql:3306").setEndTime(START + 301)
                    .setIsError(true).addAllTags(tags("db.type", "Mysql", "db.statement", "delete from t")))),
            fixture("mq-local-span", segment("orders", "orders-1", "trace-65", "seg-65",
                entry(0, -1, "/mq-local", SpanLayer.Http, 1),
                local(1, 0, "Kafka/poll").setSpanLayer(SpanLayer.MQ).addAllTags(tags("mq.topic", "orders")))),
            fixture("mq-error-without-topic-or-queue", segment("orders", "orders-1", "trace-66", "seg-66",
                entry(0, -1, "/mq-error", SpanLayer.Http, 1),
                exit(1, 0, "Kafka/Producer", SpanLayer.MQ, 40, "kafka:9092").setIsError(true))),
            fixture("mq-queue-producer", segment("orders", "orders-1", "trace-60", "seg-60",
                entry(0, -1, "/enqueue", SpanLayer.Http, 1),
                exit(1, 0, "RabbitMQ/q1/Producer", SpanLayer.MQ, 52, "rabbit:5672")
                    .addAllTags(tags("mq.queue", "q1", "mq.broker", "rabbit:5672"))))
        );
    }

    private static List<CheckerFixture> logicEndpoints() {
        final String[][] cases = {
            {"x-le-blank", "  "},
            {"x-le-json-null", "null"},
            {"x-le-scalar", "5"},
            {"x-le-array", "[1, 2]"},
            {"x-le-malformed", "{\"name\":"},
            {"x-le-missing-keys", "{\"other\":1}"},
            {"x-le-number-for-name", "{\"name\":5,\"latency\":30,\"status\":true}"},
            {"x-le-text-for-latency", "{\"name\":\"n\",\"latency\":\"abc\",\"status\":true}"},
            {"x-le-object-for-name", "{\"name\":{\"a\":1},\"latency\":30,\"status\":true}"},
            {"x-le-without-logic-span", "{\"name\":\"plain\",\"latency\":12,\"status\":false}"},
            {"x-le-logic-span-false", "{\"name\":\"n\",\"latency\":5,\"status\":true,\"logic-span\":false}"},
            {"x-le-without-latency", "{\"name\":\"n\",\"status\":true}"},
            {"x-le-without-status", "{\"name\":\"n\",\"latency\":5}"},
        };
        final List<CheckerFixture> fixtures = new ArrayList<>();
        int n = 200;
        for (final String[] c : cases) {
            fixtures.add(fixture(c[0], segment("orders", "orders-1", "trace-" + n, "seg-" + n,
                entry(0, -1, "/logic-" + n, SpanLayer.Http, 1),
                local(1, 0, "logic-" + n).addAllTags(tags("x-le", c[1])))));
            n++;
        }
        fixtures.add(fixture("x-le-logic-span-on-an-error-span", segment("orders", "orders-1", "trace-82", "seg-82",
            entry(0, -1, "/logic-error", SpanLayer.Http, 1),
            local(1, 0, "failing-logic").setIsError(true).addAllTags(tags("x-le",
                "{\"name\":\"f\",\"latency\":1,\"status\":false,\"logic-span\":true}")))));
        fixtures.add(fixture("x-le-on-entry-span", segment("orders", "orders-1", "trace-80", "seg-80",
            entry(0, -1, "/entry-logic", SpanLayer.Http, 1).addAllTags(tags("x-le",
                "{\"name\":\"on-entry\",\"latency\":3,\"status\":true,\"logic-span\":true}")))));
        fixtures.add(fixture("x-le-repeated", segment("orders", "orders-1", "trace-81", "seg-81",
            entry(0, -1, "/repeated-logic", SpanLayer.Http, 1),
            local(1, 0, "repeated-logic").addAllTags(tags(
                "x-le", "{\"name\":\"first\",\"latency\":1,\"status\":true,\"logic-span\":true}",
                "x-le", "{\"name\":\"second\",\"latency\":2,\"status\":true,\"logic-span\":true}")))));
        // "x-mF" has the String hashCode of "x-le": the key reaches the equals check of the logic-endpoint switch and
        // fails it, the branch javac 25 compiles that its JaCoCo filter does not drop.
        fixtures.add(fixture("x-le-hash-colliding-key", segment("orders", "orders-1", "trace-83", "seg-83",
            entry(0, -1, "/colliding-logic", SpanLayer.Http, 1),
            local(1, 0, "colliding-logic").addAllTags(tags(
                "x-mF", "{\"name\":\"not-logic\",\"latency\":1,\"status\":true,\"logic-span\":true}")))));
        return fixtures;
    }

    private static List<CheckerFixture> segmentValues() {
        final String longValue = "x".repeat(Tag.TAG_LENGTH - "http.method=".length());
        return List.of(
            fixture("timestamps-zero-negative-extreme", segment("orders", "orders-1", "trace-90", "seg-90",
                entry(0, -1, "/time", SpanLayer.Http, 1).setStartTime(0).setEndTime(10),
                exit(1, 0, "/neg", SpanLayer.Http, 2, "neg:80").setStartTime(-5).setEndTime(3),
                local(2, 0, "late").setStartTime(Long.MAX_VALUE - 10).setEndTime(Long.MAX_VALUE))),
            fixture("searchable-tags-at-length", segment("orders", "orders-1", "trace-91", "seg-91",
                entry(0, -1, "/long-tags", SpanLayer.Http, 1)
                    .addAllTags(tags("http.method", longValue, "http.method", longValue + "y", "db.type", "a",
                        "db.type", "a", "db.type", "b"))))
                .differs(CheckerDifference.REPEATED_TAG_FIRST_VALUE),
            fixture("searchable-tag-value-too-long", segment("orders", "orders-1", "trace-96", "seg-96",
                entry(0, -1, "/long-value", SpanLayer.Http, 1)
                    .addAllTags(tags("http.method", "v".repeat(Tag.TAG_LENGTH + 10))))),
            fixture("empty-trace-id", segment("orders", "orders-1", "", "seg-92",
                entry(0, -1, "/no-trace-id", SpanLayer.Http, 1))),
            fixture("error-on-later-spans", segment("orders", "orders-1", "trace-93", "seg-93",
                local(0, -1, "root-ok"),
                entry(1, 0, "/entry-error", SpanLayer.Http, 1).setIsError(true),
                exit(2, 0, "/exit-error", SpanLayer.Http, 2, "err:80").setIsError(true))),
            fixture("error-segment-the-policy-declines", segment("orders", "orders-1", "trace-999999", "seg-95",
                entry(0, -1, "/declined-error", SpanLayer.Http, 1).setIsError(true))),
            fixture("duration-beyond-int", segment("orders", "orders-1", "trace-94", "seg-94",
                entry(0, -1, "/very-long", SpanLayer.Http, 1).setEndTime(START + 3_000_000_000L)))
        );
    }

    private static List<CheckerFixture> genAI() {
        final String longName = "p".repeat(120);
        return List.of(
            fixture("genai-repeated-tags", genAISegment("trace-100", "seg-100", 1,
                tags("gen_ai.response.model", "gpt-5", "gen_ai.response.model", "gpt-5-pro",
                    "gen_ai.provider.name", "openai", "gen_ai.usage.input_tokens", "5"))),
            fixture("genai-missing-model", genAISegment("trace-101", "seg-101", 1,
                tags("gen_ai.provider.name", "openai", "gen_ai.usage.input_tokens", "5"))),
            fixture("genai-blank-model", genAISegment("trace-102", "seg-102", 1,
                tags("gen_ai.response.model", "  ", "gen_ai.provider.name", "openai"))),
            fixture("genai-legacy-provider", genAISegment("trace-103", "seg-103", 1,
                tags("gen_ai.response.model", "gpt-5", "gen_ai.system", "openai"))),
            fixture("genai-unparsable-numbers", genAISegment("trace-104", "seg-104", 1,
                tags("gen_ai.response.model", "gpt-5", "gen_ai.provider.name", "openai",
                    "gen_ai.usage.input_tokens", "abc", "gen_ai.usage.output_tokens", "99999999999999999999",
                    "gen_ai.server.time_to_first_token", "x"))),
            fixture("genai-long-names", genAISegment("trace-105", "seg-105", 1,
                tags("gen_ai.response.model", "m".repeat(200), "gen_ai.provider.name", longName))),
            fixture("genai-cost", genAISegment("trace-106", "seg-106", 1,
                tags("gen_ai.response.model", "gpt-5", "gen_ai.provider.name", "openai",
                    "gen_ai.usage.input_tokens", "1234567", "gen_ai.usage.output_tokens", "7654321",
                    "gen_ai.server.time_to_first_token", "250"))),
            fixture("genai-span-id-not-wire-index", genAISegment("trace-107", "seg-107", 5,
                tags("gen_ai.response.model", "gpt-5", "gen_ai.provider.name", "openai"))),
            fixture("genai-long-duration", segment("assistant", "assistant-1", "trace-108", "seg-108",
                entry(0, -1, "/ask-long", SpanLayer.Http, 1),
                exit(1, 0, "chat", SpanLayer.GenAI, 0, "api.openai.com:443").setEndTime(START + 3_000_000_000L)
                    .addAllTags(tags("gen_ai.response.model", "gpt-5", "gen_ai.provider.name", "openai")))),
            fixture("genai-before-a-failure", segment("assistant", "assistant-1", "trace-109", "seg-109",
                entry(0, -1, "/ask-then-fail", SpanLayer.Http, 1),
                exit(1, 0, "chat", SpanLayer.GenAI, 0, "api.openai.com:443")
                    .addAllTags(tags("gen_ai.response.model", "gpt-5", "gen_ai.provider.name", "openai")),
                local(2, 0, "broken-logic").addAllTags(tags("x-le", "{\"name\":")))),
            fixture("genai-entry-and-local", segment("assistant", "assistant-1", "trace-110", "seg-110",
                entry(0, -1, "/genai-entry", SpanLayer.GenAI, 0)
                    .addAllTags(tags("gen_ai.response.model", "gpt-5", "gen_ai.provider.name", "openai")),
                local(1, 0, "genai-local").setSpanLayer(SpanLayer.GenAI)
                    .addAllTags(tags("gen_ai.response.model", "gpt-5", "gen_ai.provider.name", "openai")))),
            fixture("genai-skipped", segment("assistant", "assistant-1", "trace-111", "seg-111",
                entry(0, -1, "/ask-skip", SpanLayer.Http, 1),
                exit(1, 0, "chat", SpanLayer.GenAI, 0, "api.openai.com:443").setSkipAnalysis(true)
                    .addAllTags(tags("gen_ai.response.model", "gpt-5", "gen_ai.provider.name", "openai"))))
        );
    }

    private static List<CheckerFixture> aliasesAndGateways() {
        return List.of(
            fixture("inventory-entry", segment("inventory", "inventory-1", "trace-120", "seg-120",
                entry(0, -1, "/stock", SpanLayer.Http, 1)
                    .addRefs(ref(RefType.CrossProcess, "trace-2", "seg-2", 1, "orders", "orders-1", "/orders",
                        "inventory:8080")))),
            fixture("alias-written-and-dialed-in-one-segment", segment("loop", "loop-1", "trace-121", "seg-121",
                entry(0, -1, "/loop", SpanLayer.Http, 1)
                    .addRefs(ref(RefType.CrossProcess, "trace-121", "seg-p", 1, "caller", "caller-1", "/c",
                        "loop:80")),
                exit(1, 0, "/self", SpanLayer.Http, 2, "loop:80"))),
            fixture("exit-to-alias-without-instance", segment("orders", "orders-1", "trace-122", "seg-122",
                entry(0, -1, "/noinst", SpanLayer.Http, 1),
                exit(1, 0, "/noinst-call", SpanLayer.Http, 2, NO_INSTANCE_ALIAS))),
            fixture("exit-to-malformed-alias", segment("orders", "orders-1", "trace-123", "seg-123",
                entry(0, -1, "/malformed", SpanLayer.Http, 1),
                exit(1, 0, "/malformed-call", SpanLayer.Http, 2, MALFORMED_ALIAS))),
            fixture("exit-to-virtual-alias", segment("orders", "orders-1", "trace-124", "seg-124",
                entry(0, -1, "/virtual", SpanLayer.Http, 1),
                exit(1, 0, "/virtual-call", SpanLayer.Http, 2, VIRTUAL_ALIAS))),
            bareHostGateway(),
            fixture("long-gateway-address", segment("orders", "orders-1", "trace-126", "seg-126",
                entry(0, -1, "/via-long-gateway", SpanLayer.Http, 1)
                    .addRefs(ref(RefType.CrossProcess, "trace-126", "seg-p", 1, "frontend", "frontend-1", "/home",
                        LONG_GATEWAY)),
                exit(1, 0, "/long-api", SpanLayer.Http, 2, LONG_GATEWAY))),
            fixture("exit-to-late-alias", segment("orders", "orders-1", "trace-128", "seg-128",
                entry(0, -1, "/late", SpanLayer.Http, 1),
                exit(1, 0, "/late-call", SpanLayer.Http, 2, LATE_ALIAS))),
            // An agent that cannot see the real remote address, dialing an address the warm pass aliases.
            fixture("nginx-lua-exit", segment("edge", "edge-1", "trace-127", "seg-127",
                entry(0, -1, "/edge", SpanLayer.Http, 6000),
                exit(1, 0, "/upstream", SpanLayer.Http, 6000, "inventory:8080")))
        );
    }

    /**
     * A bare host on the wire, whose gateway {@code gateways.yml} lists at port 80. Its twin names the address with the
     * port, which the listeners' exact lookup matches; v1 on the twin, with the port taken out again, is what v2 does
     * here. The gateway line maps to the bare host's first lookup, answered false: its second, at port 80, is the
     * documented line the comparison sets aside.
     */
    private static CheckerFixture bareHostGateway() {
        final String withPort = BARE_GATEWAY + ":80";
        return fixture("bare-host-gateway", gatewaySegment(BARE_GATEWAY))
            .differs(CheckerDifference.BARE_HOST_GATEWAY)
            .twin(gatewaySegment(withPort),
                "[\"" + withPort + "\", \"true\"]", "[\"" + BARE_GATEWAY + "\", \"false\"]",
                withPort, BARE_GATEWAY,
                base64(withPort), base64(BARE_GATEWAY));
    }

    private static SegmentObject gatewaySegment(final String address) {
        return segment("orders", "orders-1", "trace-125", "seg-125",
            entry(0, -1, "/via-bare-gateway", SpanLayer.Http, 1)
                .addRefs(ref(RefType.CrossProcess, "trace-125", "seg-p", 1, "frontend", "frontend-1", "/home",
                    address)),
            exit(1, 0, "/bare-api", SpanLayer.Http, 2, address));
    }

    private static String base64(final String text) {
        return base64(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String base64(final byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static SegmentObject mqConsumer(final String traceId, final String segmentId, final String latency) {
        return segment("shipping", "shipping-1", traceId, segmentId,
            entry(0, -1, "Kafka/orders/Consumer/group-1", SpanLayer.MQ, 41)
                .addRefs(ref(RefType.CrossProcess, traceId, "seg-5", 1, "orders", "orders-1", "/checkout",
                    "kafka:9092"))
                .addAllTags(tags("mq.topic", "orders", "mq.broker", "kafka:9092", "transmission.latency", latency)));
    }

    private static SegmentObject genAISegment(final String traceId, final String segmentId, final int genAISpanId,
                                              final List<KeyStringValuePair> genAITags) {
        return segment("assistant", "assistant-1", traceId, segmentId,
            entry(0, -1, "/ask", SpanLayer.Http, 1),
            exit(genAISpanId, 0, "chat", SpanLayer.GenAI, 0, "api.openai.com:443").setEndTime(START + 801)
                .addAllTags(genAITags));
    }

    private static CheckerFixture fixture(final String name, final SegmentObject segment) {
        return new CheckerFixture(name, segment);
    }

    private static SegmentObject segment(final String service, final String instance, final String traceId,
                                         final String segmentId, final SpanObject.Builder... spans) {
        final SegmentObject.Builder segment = SegmentObject.newBuilder().setService(service)
                                                           .setServiceInstance(instance).setTraceId(traceId)
                                                           .setTraceSegmentId(segmentId);
        for (final SpanObject.Builder span : spans) {
            segment.addSpans(span);
        }
        return segment.build();
    }

    private static SpanObject.Builder span(final int id, final int parent, final String operation,
                                           final SpanType type, final SpanLayer layer, final int component) {
        return SpanObject.newBuilder().setSpanId(id).setParentSpanId(parent).setOperationName(operation)
                         .setSpanType(type).setSpanLayer(layer).setComponentId(component)
                         .setStartTime(START + id).setEndTime(START + id + 10);
    }

    private static SpanObject.Builder entry(final int id, final int parent, final String operation,
                                            final SpanLayer layer, final int component) {
        return span(id, parent, operation, SpanType.Entry, layer, component);
    }

    private static SpanObject.Builder exit(final int id, final int parent, final String operation,
                                           final SpanLayer layer, final int component, final String peer) {
        return span(id, parent, operation, SpanType.Exit, layer, component).setPeer(peer);
    }

    private static SpanObject.Builder local(final int id, final int parent, final String operation) {
        return span(id, parent, operation, SpanType.Local, SpanLayer.Unknown, 0);
    }

    private static SegmentReference ref(final RefType type, final String traceId, final String parentSegment,
                                        final int parentSpan, final String parentService, final String parentInstance,
                                        final String parentEndpoint, final String addressUsedAtPeer) {
        return SegmentReference.newBuilder().setRefType(type).setTraceId(traceId)
                               .setParentTraceSegmentId(parentSegment).setParentSpanId(parentSpan)
                               .setParentService(parentService).setParentServiceInstance(parentInstance)
                               .setParentEndpoint(parentEndpoint).setNetworkAddressUsedAtPeer(addressUsedAtPeer)
                               .build();
    }

    private static List<KeyStringValuePair> tags(final String... keysAndValues) {
        final KeyStringValuePair[] pairs = new KeyStringValuePair[keysAndValues.length / 2];
        for (int i = 0; i + 1 < keysAndValues.length; i += 2) {
            pairs[i / 2] = KeyStringValuePair.newBuilder().setKey(keysAndValues[i]).setValue(keysAndValues[i + 1])
                                             .build();
        }
        return List.of(pairs);
    }
}
