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

package org.apache.skywalking.oap.server.receiver.trace.provider.parser.listener;

import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.network.language.agent.v3.RefType;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.UninstrumentedGatewaysConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.EndpointDepFromCrossThreadAnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.RPCAnalysisListener;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher.ConfigChangeEvent;
import org.apache.skywalking.oap.server.configuration.api.ConfigChangeWatcher.EventType;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.ServerClusterConfig;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.EndpointMeta;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceRelation;
import org.apache.skywalking.oap.server.core.source.ServiceRelation;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The native trace analysis names a broker, a database or a cache by the cluster {@code server-clusters.yml} lists
 * its address under, when no network alias maps the address. The peers are those the SkyWalking Java agent's plugins
 * report: Kafka's whole bootstrap list joined by {@code ;}, multi-host JDBC's hosts joined by {@code ,}.
 */
public class ServerClusterNameAnalysisTest {
    private static final NamingControl NAMING_CONTROL = new NamingControl(70, 100, 100, new EndpointNameGrouping());

    private final AnalyzerModuleConfig config = mock(AnalyzerModuleConfig.class);
    private final UninstrumentedGatewaysConfig gateways = mock(UninstrumentedGatewaysConfig.class);
    private final NetworkAddressAliasCache noAlias = mock(NetworkAddressAliasCache.class);
    private ServerClusterConfig clusters;

    @BeforeEach
    public void setUp() {
        when(config.getUninstrumentedGatewaysConfig()).thenReturn(gateways);
        when(gateways.isAddressConfiguredAsGateway(any())).thenReturn(false);
        when(noAlias.get(any())).thenReturn(null);
        clusters = new ServerClusterConfig(mock(ModuleProvider.class), 70);
        clusters.notify(new ConfigChangeEvent(String.join("\n",
            "clusters:",
            "  - name: kafka-prod",
            "    aliases: [kafka:9092, kafka-node:9092]",
            "  - name: orders-db",
            "    members: [pg-0:5432, pg-1:5432]",
            "  - name: edge",
            "    aliases: [nginx:80]"
        ), EventType.MODIFY));
    }

    @Test
    public void anExitToADatabaseIsNamedAsItsCluster() {
        final MockReceiver receiver = new MockReceiver();
        final RPCAnalysisListener listener = new RPCAnalysisListener(
            receiver, config, noAlias, NAMING_CONTROL, clusters);
        listener.parseExit(exitSpan(SpanLayer.Database, "pg-1:5432,pg-0:5432"), segment());
        listener.build();
        final List<ISource> sources = receiver.getReceivedSources();
        assertEquals("orders-db", ((ServiceRelation) sources.get(0)).getDestServiceName());
        assertEquals("orders-db", ((ServiceInstanceRelation) sources.get(1)).getDestServiceInstanceName(),
                     "no instance is generated for an address");
    }

    @Test
    public void aNetworkAliasWinsOverTheCluster() {
        final NetworkAddressAliasCache aliases = mock(NetworkAddressAliasCache.class);
        final NetworkAddressAlias alias = new NetworkAddressAlias();
        final String serviceId = IDManager.ServiceID.buildId("target-service", true);
        alias.setRepresentServiceId(serviceId);
        alias.setRepresentServiceInstanceId(IDManager.ServiceInstanceID.buildId(serviceId, "target-instance"));
        when(aliases.get(any())).thenReturn(alias);
        final MockReceiver receiver = new MockReceiver();
        final RPCAnalysisListener listener = new RPCAnalysisListener(
            receiver, config, aliases, NAMING_CONTROL, clusters);
        listener.parseExit(exitSpan(SpanLayer.MQ, "kafka:9092;kafka-node:9092"), segment());
        listener.build();
        assertEquals("target-service", ((ServiceRelation) receiver.getReceivedSources().get(0)).getDestServiceName());
    }

    @Test
    public void anHttpPeerIsNotLookedUp() {
        final MockReceiver receiver = new MockReceiver();
        final RPCAnalysisListener listener = new RPCAnalysisListener(
            receiver, config, noAlias, NAMING_CONTROL, clusters);
        listener.parseExit(exitSpan(SpanLayer.Http, "nginx:80"), segment());
        listener.build();
        assertEquals("nginx:80", ((ServiceRelation) receiver.getReceivedSources().get(0)).getDestServiceName());
    }

    @Test
    public void theBrokerOfAConsumedMessageIsNamedAsItsCluster() {
        final MockReceiver receiver = new MockReceiver();
        final RPCAnalysisListener listener = new RPCAnalysisListener(
            receiver, config, noAlias, NAMING_CONTROL, clusters);
        listener.parseEntry(entrySpan(SpanLayer.MQ, "kafka-node:9092;kafka:9092"), segment());
        listener.parseEntry(SpanObject.newBuilder()
                                      .setOperationName("Kafka/orders/Consumer").setSpanType(SpanType.Entry)
                                      .setSpanLayer(SpanLayer.MQ).setPeer("kafka:9092;kafka-node:9092")
                                      .setStartTime(1_700_000_000_000L).setEndTime(1_700_000_000_010L)
                                      .build(), segment());
        listener.build();
        final List<ServiceRelation> relations = relations(receiver);
        assertEquals(2, relations.size());
        assertEquals("kafka-prod", relations.get(0).getSourceServiceName(), "named by the producer's address");
        assertEquals("kafka-prod", relations.get(1).getSourceServiceName(), "named by the consumer's own peer");
    }

    @Test
    public void aGatewayTheProducerDialedKeepsItsAddress() {
        when(gateways.isAddressConfiguredAsGateway("nginx:80")).thenReturn(true);
        final MockReceiver receiver = new MockReceiver();
        final RPCAnalysisListener listener = new RPCAnalysisListener(
            receiver, config, noAlias, NAMING_CONTROL, clusters);
        listener.parseEntry(entrySpan(SpanLayer.Http, "nginx:80"), segment());
        listener.build();
        assertEquals("nginx:80", relations(receiver).get(0).getSourceServiceName());
    }

    @Test
    public void theCrossThreadEndpointOfABrokerIsNamedAsItsCluster() {
        final MockReceiver receiver = new MockReceiver();
        final EndpointDepFromCrossThreadAnalysisListener listener = new EndpointDepFromCrossThreadAnalysisListener(
            receiver, config, NAMING_CONTROL, clusters);
        listener.parseLocal(SpanObject.newBuilder()
                                      .setOperationName("handle").setSpanType(SpanType.Local)
                                      .setSpanLayer(SpanLayer.MQ)
                                      .addRefs(SegmentReference.newBuilder()
                                                               .setRefType(RefType.CrossThread)
                                                               .setParentService("producer")
                                                               .setParentServiceInstance("producer-1")
                                                               .setParentEndpoint("send")
                                                               .setNetworkAddressUsedAtPeer(
                                                                   "kafka:9092;kafka-node:9092"))
                                      .setStartTime(1_700_000_000_000L).setEndTime(1_700_000_000_010L)
                                      .build(), segment());
        listener.build();
        assertEquals("kafka-prod", ((EndpointMeta) receiver.getReceivedSources().get(0)).getServiceName());
    }

    private static SpanObject exitSpan(final SpanLayer layer, final String peer) {
        return SpanObject.newBuilder()
                         .setOperationName("call").setSpanType(SpanType.Exit).setSpanLayer(layer).setPeer(peer)
                         .setStartTime(1_700_000_000_000L).setEndTime(1_700_000_000_010L)
                         .build();
    }

    private static SpanObject entrySpan(final SpanLayer layer, final String addressUsedAtPeer) {
        return SpanObject.newBuilder()
                         .setOperationName("Kafka/orders/Consumer").setSpanType(SpanType.Entry).setSpanLayer(layer)
                         .addRefs(SegmentReference.newBuilder()
                                                  .setRefType(RefType.CrossProcess)
                                                  .setParentService("producer")
                                                  .setParentServiceInstance("producer-1")
                                                  .setParentEndpoint("send")
                                                  .setNetworkAddressUsedAtPeer(addressUsedAtPeer))
                         .setStartTime(1_700_000_000_000L).setEndTime(1_700_000_000_010L)
                         .build();
    }

    private static SegmentObject segment() {
        return SegmentObject.newBuilder().setService("consumer").setServiceInstance("consumer-1").build();
    }

    private static List<ServiceRelation> relations(final MockReceiver receiver) {
        return receiver.getReceivedSources().stream()
                       .filter(ServiceRelation.class::isInstance)
                       .map(ServiceRelation.class::cast)
                       .collect(Collectors.toList());
    }
}
