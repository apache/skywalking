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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.vservice;

import java.util.ArrayList;
import java.util.List;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.CacheReadLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.DBLatencyThresholdsAndWatcher;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SpanTags;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher.ConfigChangeEvent;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher.EventType;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.ServerClusterConfig;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.DatabaseSlowStatement;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.Source;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

/**
 * The virtual MQ, database and cache services take the cluster {@code server-clusters.yml} lists their reported
 * address under, as the SkyWalking Java agent's plugins report them: a whole configured list for Kafka and multi-host
 * JDBC, one node for a plain Redis client.
 */
public class VirtualServiceClusterNameTest {
    private final NamingControl namingControl = new NamingControl(512, 512, 512, new EndpointNameGrouping());
    private ServerClusterConfig clusters;

    @BeforeEach
    public void setUp() {
        clusters = new ServerClusterConfig(mock(ModuleProvider.class), 70);
        clusters.notify(new ConfigChangeEvent(String.join("\n",
            "clusters:",
            "  - name: kafka-prod",
            "    aliases: [kafka:9092, kafka-node:9092]",
            "  - name: orders-db",
            "    members: [pg-0:5432, pg-1:5432]",
            "  - name: redis-prod",
            "    members: [redis-0:6379]",
            "    aliases: [redis-local]"
        ), EventType.MODIFY));
    }

    @Test
    public void aBrokerIsNamedAsItsClusterOnBothSides() {
        final VirtualMQProcessor processor = new VirtualMQProcessor(namingControl, clusters);
        processor.prepareVSIfNecessary(
            mqSpan(SpanType.Exit).setPeer("kafka:9092;kafka-node:9092").build(), SegmentObject.newBuilder().build());
        processor.prepareVSIfNecessary(
            mqSpan(SpanType.Entry).setPeer("kafka-node:9092;kafka:9092")
                                  .addRefs(SegmentReference.newBuilder()
                                                           .setNetworkAddressUsedAtPeer("kafka-node:9092;kafka:9092"))
                                  .build(),
            SegmentObject.newBuilder().build());
        final List<String> services = serviceMetaNames(processor);
        assertEquals(List.of("kafka-prod", "kafka-prod"), services, "the producer's and the consumer's broker");
    }

    @Test
    public void aDatabaseAndItsSlowStatementsAreNamedAsItsCluster() {
        final AnalyzerModuleConfig config = new AnalyzerModuleConfig();
        config.setDbLatencyThresholdsAndWatcher(new DBLatencyThresholdsAndWatcher("default:10", null));
        final VirtualDatabaseProcessor processor = new VirtualDatabaseProcessor(namingControl, config, clusters);
        processor.prepareVSIfNecessary(
            SpanObject.newBuilder()
                      .setSpanLayer(SpanLayer.Database).setSpanType(SpanType.Exit).setPeer("pg-1:5432,pg-0:5432")
                      .addTags(tag(SpanTags.DB_STATEMENT, "select 1")).addTags(tag(SpanTags.DB_TYPE, "PostgreSQL"))
                      .setStartTime(1_700_000_000_000L).setEndTime(1_700_000_001_000L)
                      .build(),
            SegmentObject.newBuilder().setTraceId("t1").setService("orders").build());
        final List<Source> sources = new ArrayList<>();
        processor.emitTo(sources::add);
        assertEquals("orders-db", ((ServiceMeta) sources.get(0)).getName());
        assertEquals(IDManager.ServiceID.buildId("orders-db", false),
                     ((DatabaseSlowStatement) sources.get(2)).getDatabaseServiceId());
    }

    @Test
    public void aCacheIsNamedAsItsClusterButALocalCacheIsNotLookedUp() {
        final AnalyzerModuleConfig config = new AnalyzerModuleConfig();
        config.setCacheReadLatencyThresholdsAndWatcher(new CacheReadLatencyThresholdsAndWatcher("default:10", null));
        final VirtualCacheProcessor processor = new VirtualCacheProcessor(namingControl, config, clusters);
        processor.prepareVSIfNecessary(cacheSpan("redis-0:6379"), SegmentObject.newBuilder().build());
        processor.prepareVSIfNecessary(cacheSpan(""), SegmentObject.newBuilder().build());
        assertEquals(List.of("redis-prod", "redis-local"), serviceMetaNames(processor),
                     "a span that reports no address keeps the local cache name");
    }

    @Test
    public void anUnlistedAddressKeepsItsName() {
        final VirtualMQProcessor processor = new VirtualMQProcessor(namingControl, clusters);
        processor.prepareVSIfNecessary(
            mqSpan(SpanType.Exit).setPeer("kafka:9092;other:9092").build(), SegmentObject.newBuilder().build());
        assertEquals(List.of("kafka:9092;other:9092"), serviceMetaNames(processor));
    }

    private static SpanObject.Builder mqSpan(final SpanType type) {
        return SpanObject.newBuilder()
                         .setSpanLayer(SpanLayer.MQ).setSpanType(type)
                         .addTags(tag(SpanTags.MQ_TOPIC, "orders"))
                         .setStartTime(1_700_000_000_000L).setEndTime(1_700_000_000_010L);
    }

    private static SpanObject cacheSpan(final String peer) {
        return SpanObject.newBuilder()
                         .setSpanLayer(SpanLayer.Cache).setSpanType(SpanType.Exit).setPeer(peer)
                         .addTags(tag(SpanTags.CACHE_TYPE, "redis")).addTags(tag(SpanTags.CACHE_CMD, "get"))
                         .addTags(tag(SpanTags.CACHE_OP, "read")).addTags(tag(SpanTags.CACHE_KEY, "k"))
                         .setStartTime(1_700_000_000_000L).setEndTime(1_700_000_000_002L)
                         .build();
    }

    private static KeyStringValuePair tag(final String key, final String value) {
        return KeyStringValuePair.newBuilder().setKey(key).setValue(value).build();
    }

    private static List<String> serviceMetaNames(final VirtualServiceProcessor processor) {
        final List<String> names = new ArrayList<>();
        processor.emitTo(source -> {
            if (source instanceof ServiceMeta) {
                names.add(((ServiceMeta) source).getName());
            }
        });
        return names;
    }
}
