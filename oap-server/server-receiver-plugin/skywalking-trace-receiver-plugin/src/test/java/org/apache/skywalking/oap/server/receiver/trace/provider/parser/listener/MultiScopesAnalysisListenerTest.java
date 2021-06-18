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

import com.google.gson.JsonObject;
import java.util.List;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.RefType;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.UninstrumentedGatewaysConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SpanTags;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.AnalysisListener;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.MultiScopesAnalysisListener;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.All;
import org.apache.skywalking.oap.server.core.source.DatabaseAccess;
import org.apache.skywalking.oap.server.core.source.Endpoint;
import org.apache.skywalking.oap.server.core.source.EndpointRelation;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.Service;
import org.apache.skywalking.oap.server.core.source.ServiceInstance;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceRelation;
import org.apache.skywalking.oap.server.core.source.ServiceMeta;
import org.apache.skywalking.oap.server.core.source.ServiceRelation;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SpanTags.LOGIC_ENDPOINT;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * MultiScopesSpanListener includes the most segment to source(s) logic. This test covers most cases about the segment
 * to sources translation.
 *
 * This test is a good way to study about how OAP analysis trace segment.
 */
public class MultiScopesAnalysisListenerTest {
    @Mock
    private static AnalyzerModuleConfig CONFIG;
    @Mock
    private static NetworkAddressAliasCache CACHE;
    @Mock
    private static NetworkAddressAliasCache CACHE2;
    private static NamingControl NAMING_CONTROL = new NamingControl(
        70,
        100,
        100,
        new EndpointNameGrouping()
    );

    @Before
    public void init() {
        MockitoAnnotations.initMocks(this);

        when(CACHE.get(any())).thenReturn(null);
        final NetworkAddressAlias networkAddressAlias = new NetworkAddressAlias();
        final String serviceId = IDManager.ServiceID.buildId("target-service", true);
        final String instanceId = IDManager.ServiceInstanceID.buildId(serviceId, "target-instance");
        networkAddressAlias.setRepresentServiceId(serviceId);
        networkAddressAlias.setRepresentServiceInstanceId(instanceId);
        when(CACHE2.get(any())).thenReturn(networkAddressAlias);
        final UninstrumentedGatewaysConfig uninstrumentedGatewaysConfig = Mockito.mock(
            UninstrumentedGatewaysConfig.class);
        when(uninstrumentedGatewaysConfig.isAddressConfiguredAsGateway(any())).thenReturn(false);
        when(CONFIG.getUninstrumentedGatewaysConfig()).thenReturn(uninstrumentedGatewaysConfig);
    }

    @Test
    public void testContainsPoint() {
        MultiScopesAnalysisListener listener = new MultiScopesAnalysisListener(
            new MockReceiver(),
            CONFIG,
            CACHE,
            NAMING_CONTROL
        );
        Assert.assertTrue(listener.containsPoint(AnalysisListener.Point.Entry));
        Assert.assertTrue(listener.containsPoint(AnalysisListener.Point.Local));
        Assert.assertTrue(listener.containsPoint(AnalysisListener.Point.Exit));
        Assert.assertFalse(listener.containsPoint(AnalysisListener.Point.First));
        Assert.assertFalse(listener.containsPoint(AnalysisListener.Point.Segment));
    }

    /**
     * Entry span without ref, usually the first span of the whole trace.
     */
    @Test
    public void testEntrySpanWithoutRef() {
        final MockReceiver mockReceiver = new MockReceiver();
        MultiScopesAnalysisListener listener = new MultiScopesAnalysisListener(
            mockReceiver,
            CONFIG,
            CACHE,
            NAMING_CONTROL
        );

        final long startTime = System.currentTimeMillis();
        SpanObject spanObject = SpanObject.newBuilder()
                                          .setOperationName("/springMVC")
                                          .setStartTime(startTime)
                                          .setEndTime(startTime + 1000L)
                                          .setIsError(true)
                                          .setSpanType(SpanType.Entry)
                                          .addTags(
                                              KeyStringValuePair.newBuilder()
                                                                .setKey(SpanTags.STATUS_CODE)
                                                                .setValue("500")
                                                                .build()
                                          )
                                          .build();
        final SegmentObject segment = SegmentObject.newBuilder()
                                                   .setService("mock-service")
                                                   .setServiceInstance("mock-instance")
                                                   .addSpans(spanObject)
                                                   .build();
        listener.parseEntry(spanObject, segment);
        listener.build();

        final List<ISource> receivedSources = mockReceiver.getReceivedSources();
        Assert.assertEquals(7, receivedSources.size());
        final All all = (All) receivedSources.get(0);
        final Service service = (Service) receivedSources.get(1);
        final ServiceInstance serviceInstance = (ServiceInstance) receivedSources.get(2);
        final Endpoint endpoint = (Endpoint) receivedSources.get(3);
        final ServiceRelation serviceRelation = (ServiceRelation) receivedSources.get(4);
        final ServiceInstanceRelation serviceInstanceRelation = (ServiceInstanceRelation) receivedSources.get(5);
        final EndpointRelation endpointRelation = (EndpointRelation) receivedSources.get(6);
        Assert.assertEquals("mock-service", service.getName());
        Assert.assertEquals(500, service.getResponseCode());
        Assert.assertFalse(service.isStatus());
        Assert.assertEquals("mock-instance", serviceInstance.getName());
        Assert.assertEquals("/springMVC", endpoint.getName());
        Assert.assertEquals(Const.USER_SERVICE_NAME, serviceRelation.getSourceServiceName());
        Assert.assertEquals(service.getName(), serviceRelation.getDestServiceName());
        Assert.assertEquals(Const.USER_INSTANCE_NAME, serviceInstanceRelation.getSourceServiceInstanceName());
        Assert.assertEquals(serviceInstance.getName(), serviceInstanceRelation.getDestServiceInstanceName());
        Assert.assertEquals(Const.USER_ENDPOINT_NAME, endpointRelation.getEndpoint());
        Assert.assertEquals(endpoint.getName(), endpointRelation.getChildEndpoint());
    }

    /**
     * Entry span with ref, meaning the downstream has been instrumented.
     */
    @Test
    public void testEntrySpanRef() {
        final MockReceiver mockReceiver = new MockReceiver();
        MultiScopesAnalysisListener listener = new MultiScopesAnalysisListener(
            mockReceiver,
            CONFIG,
            CACHE,
            NAMING_CONTROL
        );

        final long startTime = System.currentTimeMillis();
        SpanObject spanObject = SpanObject.newBuilder()
                                          .setOperationName("/springMVC")
                                          .setStartTime(startTime)
                                          .setEndTime(startTime + 1000L)
                                          .setIsError(true)
                                          .setSpanType(SpanType.Entry)
                                          .setSpanLayer(SpanLayer.RPCFramework)
                                          .addTags(KeyStringValuePair.newBuilder()
                                                                     .setKey("http.method")
                                                                     .setValue("GET")
                                                                     .build())
                                          .addRefs(
                                              SegmentReference.newBuilder()
                                                              .setRefType(RefType.CrossProcess)
                                                              .setParentService("downstream-service")
                                                              .setParentServiceInstance("downstream-instance")
                                                              .setParentEndpoint("downstream-endpoint")
                                                              .setNetworkAddressUsedAtPeer("127.0.0.1")
                                                              .build()
                                          )
                                          .build();
        final SegmentObject segment = SegmentObject.newBuilder()
                                                   .setService("mock-service")
                                                   .setServiceInstance("mock-instance")
                                                   .addSpans(spanObject)
                                                   .build();
        listener.parseEntry(spanObject, segment);
        listener.build();

        final List<ISource> receivedSources = mockReceiver.getReceivedSources();
        Assert.assertEquals(7, receivedSources.size());
        final All all = (All) receivedSources.get(0);
        final Service service = (Service) receivedSources.get(1);
        final ServiceInstance serviceInstance = (ServiceInstance) receivedSources.get(2);
        final Endpoint endpoint = (Endpoint) receivedSources.get(3);
        final ServiceRelation serviceRelation = (ServiceRelation) receivedSources.get(4);
        final ServiceInstanceRelation serviceInstanceRelation = (ServiceInstanceRelation) receivedSources.get(5);
        final EndpointRelation endpointRelation = (EndpointRelation) receivedSources.get(6);
        Assert.assertEquals("mock-service", service.getName());
        Assert.assertEquals("mock-instance", serviceInstance.getName());
        Assert.assertEquals("/springMVC", endpoint.getName());
        Assert.assertEquals("downstream-service", serviceRelation.getSourceServiceName());
        Assert.assertEquals(service.getName(), serviceRelation.getDestServiceName());
        Assert.assertEquals("downstream-instance", serviceInstanceRelation.getSourceServiceInstanceName());
        Assert.assertEquals(serviceInstance.getName(), serviceInstanceRelation.getDestServiceInstanceName());
        Assert.assertEquals("downstream-endpoint", endpointRelation.getEndpoint());
        Assert.assertEquals(endpoint.getName(), endpointRelation.getChildEndpoint());
        // tags test
        Assert.assertEquals("http.method:GET", all.getTags().get(0));
        Assert.assertEquals("http.method:GET", service.getTags().get(0));
        Assert.assertEquals("http.method:GET", serviceInstance.getTags().get(0));
        Assert.assertEquals("http.method:GET", endpoint.getTags().get(0));
    }

    /**
     * Entry span with ref, but as a MQ server, or uninstrumented server.
     */
    @Test
    public void testEntrySpanMQRef() {
        final MockReceiver mockReceiver = new MockReceiver();
        MultiScopesAnalysisListener listener = new MultiScopesAnalysisListener(
            mockReceiver,
            CONFIG,
            CACHE,
            NAMING_CONTROL
        );

        final long startTime = System.currentTimeMillis();
        SpanObject spanObject = SpanObject.newBuilder()
                                          .setOperationName("/springMVC")
                                          .setStartTime(startTime)
                                          .setEndTime(startTime + 1000L)
                                          .setIsError(true)
                                          .setSpanType(SpanType.Entry)
                                          .setSpanLayer(SpanLayer.MQ)
                                          .addRefs(
                                              SegmentReference.newBuilder()
                                                              .setRefType(RefType.CrossProcess)
                                                              .setParentService("downstream-service")
                                                              .setParentServiceInstance("downstream-instance")
                                                              .setParentEndpoint("downstream-endpoint")
                                                              .setNetworkAddressUsedAtPeer("127.0.0.1")
                                                              .build()
                                          )
                                          .build();
        final SegmentObject segment = SegmentObject.newBuilder()
                                                   .setService("mock-service")
                                                   .setServiceInstance("mock-instance")
                                                   .addSpans(spanObject)
                                                   .build();
        listener.parseEntry(spanObject, segment);
        listener.build();

        final List<ISource> receivedSources = mockReceiver.getReceivedSources();
        Assert.assertEquals(7, receivedSources.size());
        final All all = (All) receivedSources.get(0);
        final Service service = (Service) receivedSources.get(1);
        final ServiceInstance serviceInstance = (ServiceInstance) receivedSources.get(2);
        final Endpoint endpoint = (Endpoint) receivedSources.get(3);
        final ServiceRelation serviceRelation = (ServiceRelation) receivedSources.get(4);
        final ServiceInstanceRelation serviceInstanceRelation = (ServiceInstanceRelation) receivedSources.get(5);
        final EndpointRelation endpointRelation = (EndpointRelation) receivedSources.get(6);
        Assert.assertEquals("mock-service", service.getName());
        Assert.assertEquals("mock-instance", serviceInstance.getName());
        Assert.assertEquals("/springMVC", endpoint.getName());
        Assert.assertEquals("127.0.0.1", serviceRelation.getSourceServiceName());
        Assert.assertEquals(service.getName(), serviceRelation.getDestServiceName());
        Assert.assertEquals("127.0.0.1", serviceInstanceRelation.getSourceServiceInstanceName());
        Assert.assertEquals(serviceInstance.getName(), serviceInstanceRelation.getDestServiceInstanceName());
        Assert.assertEquals("downstream-endpoint", endpointRelation.getEndpoint());
        Assert.assertEquals("downstream-service", endpointRelation.getServiceName());
        Assert.assertEquals(endpoint.getName(), endpointRelation.getChildEndpoint());
    }

    /**
     * Local span analysis is triggered with logic span tag.
     */
    @Test
    public void testParseLocalLogicSpan() {
        final MockReceiver mockReceiver = new MockReceiver();
        MultiScopesAnalysisListener listener = new MultiScopesAnalysisListener(
            mockReceiver,
            CONFIG,
            CACHE,
            NAMING_CONTROL
        );

        final long startTime = System.currentTimeMillis();
        final JsonObject logicSpanTagValue = new JsonObject();
        logicSpanTagValue.addProperty("logic-span", true);
        SpanObject spanObject = SpanObject.newBuilder()
                                          .setOperationName("/logic-call")
                                          .setStartTime(startTime)
                                          .setEndTime(startTime + 1000L)
                                          .setIsError(false)
                                          .setSpanType(SpanType.Local)
                                          .addTags(KeyStringValuePair.newBuilder()
                                                                     .setKey(LOGIC_ENDPOINT)
                                                                     .setValue(logicSpanTagValue.toString())
                                                                     .build())
                                          .build();
        final SegmentObject segment = SegmentObject.newBuilder()
                                                   .setService("mock-service")
                                                   .setServiceInstance("mock-instance")
                                                   .addSpans(spanObject)
                                                   .build();
        listener.parseLocal(spanObject, segment);
        listener.build();

        final List<ISource> receivedSources = mockReceiver.getReceivedSources();
        Assert.assertEquals(1, receivedSources.size());
        final Endpoint source = (Endpoint) receivedSources.get(0);
        Assert.assertEquals("/logic-call", source.getName());

        mockReceiver.clear();
    }

    /**
     * Local span analysis is triggered with extension logic service tags.
     */
    @Test
    public void testParseSpanWithLogicEndpointTag() {
        final MockReceiver mockReceiver = new MockReceiver();
        MultiScopesAnalysisListener listener = new MultiScopesAnalysisListener(
            mockReceiver,
            CONFIG,
            CACHE,
            NAMING_CONTROL
        );

        final long startTime = System.currentTimeMillis();
        final JsonObject logicSpanTagValue = new JsonObject();
        logicSpanTagValue.addProperty("name", "/GraphQL-service");
        logicSpanTagValue.addProperty("latency", 100);
        logicSpanTagValue.addProperty("status", false);
        SpanObject spanObject = SpanObject.newBuilder()
                                          .setOperationName("/logic-call")
                                          .setStartTime(startTime)
                                          .setEndTime(startTime + 1000L)
                                          .setIsError(false)
                                          .setSpanType(SpanType.Local)
                                          .addTags(KeyStringValuePair.newBuilder()
                                                                     .setKey(LOGIC_ENDPOINT)
                                                                     .setValue(logicSpanTagValue.toString())
                                                                     .build())
                                          .build();
        final SegmentObject segment = SegmentObject.newBuilder()
                                                   .setService("mock-service")
                                                   .setServiceInstance("mock-instance")
                                                   .addSpans(spanObject)
                                                   .build();
        listener.parseLocal(spanObject, segment);
        listener.build();

        final List<ISource> receivedSources = mockReceiver.getReceivedSources();
        Assert.assertEquals(1, receivedSources.size());
        final Endpoint source = (Endpoint) receivedSources.get(0);
        Assert.assertEquals("/GraphQL-service", source.getName());

        mockReceiver.clear();
    }

    /**
     * Exit span, represent calling a 3rd party system, when the alias has not been setup, including access database.
     */
    @Test
    public void testExitSpanWithoutAlias() {
        final MockReceiver mockReceiver = new MockReceiver();
        MultiScopesAnalysisListener listener = new MultiScopesAnalysisListener(
            mockReceiver,
            CONFIG,
            CACHE,
            NAMING_CONTROL
        );

        final long startTime = System.currentTimeMillis();
        SpanObject spanObject = SpanObject.newBuilder()
                                          .setOperationName("/springMVC")
                                          .setStartTime(startTime)
                                          .setEndTime(startTime + 1000L)
                                          .setIsError(true)
                                          .setSpanType(SpanType.Exit)
                                          .setSpanLayer(SpanLayer.Database)
                                          .setPeer("127.0.0.1:8080")
                                          .build();
        final SegmentObject segment = SegmentObject.newBuilder()
                                                   .setService("mock-service")
                                                   .setServiceInstance("mock-instance")
                                                   .addSpans(spanObject)
                                                   .build();
        listener.parseExit(spanObject, segment);
        listener.build();

        final List<ISource> receivedSources = mockReceiver.getReceivedSources();
        Assert.assertEquals(4, receivedSources.size());
        final ServiceRelation serviceRelation = (ServiceRelation) receivedSources.get(0);
        final ServiceInstanceRelation serviceInstanceRelation = (ServiceInstanceRelation) receivedSources.get(1);
        final ServiceMeta serviceMeta = (ServiceMeta) receivedSources.get(2);
        final DatabaseAccess databaseAccess = (DatabaseAccess) receivedSources.get(3);
        Assert.assertEquals("mock-service", serviceRelation.getSourceServiceName());
        Assert.assertEquals("127.0.0.1:8080", serviceRelation.getDestServiceName());
        Assert.assertEquals("mock-instance", serviceInstanceRelation.getSourceServiceInstanceName());
        Assert.assertEquals("127.0.0.1:8080", serviceInstanceRelation.getDestServiceInstanceName());
        Assert.assertEquals("127.0.0.1:8080", serviceMeta.getName());
        Assert.assertEquals(NodeType.Database, serviceMeta.getNodeType());
        Assert.assertEquals("127.0.0.1:8080", databaseAccess.getName());
    }

    /**
     * Exit span, represent calling a 3rd party system, when the alias has been setup.
     */
    @Test
    public void testExitSpanWithAlias() {
        final MockReceiver mockReceiver = new MockReceiver();
        MultiScopesAnalysisListener listener = new MultiScopesAnalysisListener(
            mockReceiver,
            CONFIG,
            CACHE2,
            NAMING_CONTROL
        );

        final long startTime = System.currentTimeMillis();
        SpanObject spanObject = SpanObject.newBuilder()
                                          .setOperationName("/springMVC")
                                          .setStartTime(startTime)
                                          .setEndTime(startTime + 1000L)
                                          .setIsError(true)
                                          .setSpanType(SpanType.Exit)
                                          .setSpanLayer(SpanLayer.MQ)
                                          .setPeer("127.0.0.1:8080")
                                          .build();
        final SegmentObject segment = SegmentObject.newBuilder()
                                                   .setService("mock-service")
                                                   .setServiceInstance("mock-instance")
                                                   .addSpans(spanObject)
                                                   .build();
        listener.parseExit(spanObject, segment);
        listener.build();

        final List<ISource> receivedSources = mockReceiver.getReceivedSources();
        Assert.assertEquals(2, receivedSources.size());
        final ServiceRelation serviceRelation = (ServiceRelation) receivedSources.get(0);
        final ServiceInstanceRelation serviceInstanceRelation = (ServiceInstanceRelation) receivedSources.get(1);
        Assert.assertEquals("mock-service", serviceRelation.getSourceServiceName());
        Assert.assertEquals("target-service", serviceRelation.getDestServiceName());
        Assert.assertEquals("mock-instance", serviceInstanceRelation.getSourceServiceInstanceName());
        Assert.assertEquals("target-instance", serviceInstanceRelation.getDestServiceInstanceName());
        mockReceiver.clear();
    }
}
