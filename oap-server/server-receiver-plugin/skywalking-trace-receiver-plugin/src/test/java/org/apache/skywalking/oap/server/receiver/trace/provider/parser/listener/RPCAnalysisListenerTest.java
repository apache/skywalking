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
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.RPCAnalysisListener;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.core.cache.NetworkAddressAliasCache;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.Endpoint;
import org.apache.skywalking.oap.server.core.source.EndpointRelation;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.RequestType;
import org.apache.skywalking.oap.server.core.source.Service;
import org.apache.skywalking.oap.server.core.source.ServiceInstance;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceRelation;
import org.apache.skywalking.oap.server.core.source.ServiceRelation;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.apache.skywalking.oap.server.analyzer.provider.trace.parser.SpanTags.LOGIC_ENDPOINT;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * RPCAnalysisListenerTest includes the most segment to source(s) logic. This test covers most cases about the segment
 * to sources translation.
 *
 * This test is a good way to study about how OAP analysis trace segment.
 */
public class RPCAnalysisListenerTest {
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

    @BeforeEach
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
        RPCAnalysisListener listener = new RPCAnalysisListener(
            new MockReceiver(),
            CONFIG,
            CACHE,
            NAMING_CONTROL
        );
        Assertions.assertTrue(listener.containsPoint(AnalysisListener.Point.Entry));
        Assertions.assertTrue(listener.containsPoint(AnalysisListener.Point.Local));
        Assertions.assertTrue(listener.containsPoint(AnalysisListener.Point.Exit));
        Assertions.assertFalse(listener.containsPoint(AnalysisListener.Point.First));
        Assertions.assertFalse(listener.containsPoint(AnalysisListener.Point.Segment));
    }

    /**
     * Entry span without ref, usually the first span of the whole trace.
     */
    @Test
    public void testEntrySpanWithoutRef() {
        final MockReceiver mockReceiver = new MockReceiver();
        RPCAnalysisListener listener = new RPCAnalysisListener(
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
                                                                .setKey(SpanTags.HTTP_RESPONSE_STATUS_CODE)
                                                                .setValue("500")
                                                                .build()
                                          )
                                          .addTags(
                                              KeyStringValuePair.newBuilder()
                                                                .setKey(SpanTags.RPC_RESPONSE_STATUS_CODE)
                                                                .setValue("OK")
                                                                .build())
                                          .build();
        final SegmentObject segment = SegmentObject.newBuilder()
                                                   .setService("mock-service")
                                                   .setServiceInstance("mock-instance")
                                                   .addSpans(spanObject)
                                                   .build();
        listener.parseEntry(spanObject, segment);
        listener.build();

        final List<ISource> receivedSources = mockReceiver.getReceivedSources();
        Assertions.assertEquals(6, receivedSources.size());
        final Service service = (Service) receivedSources.get(0);
        final ServiceInstance serviceInstance = (ServiceInstance) receivedSources.get(1);
        final ServiceRelation serviceRelation = (ServiceRelation) receivedSources.get(2);
        final ServiceInstanceRelation serviceInstanceRelation = (ServiceInstanceRelation) receivedSources.get(3);
        final Endpoint endpoint = (Endpoint) receivedSources.get(4);
        final EndpointRelation endpointRelation = (EndpointRelation) receivedSources.get(5);
        Assertions.assertEquals("mock-service", service.getName());
        Assertions.assertEquals(500, service.getHttpResponseStatusCode());
        Assertions.assertEquals("OK", service.getRpcStatusCode());
        Assertions.assertFalse(service.isStatus());
        Assertions.assertEquals("mock-instance", serviceInstance.getName());
        Assertions.assertEquals("/springMVC", endpoint.getName());
        Assertions.assertEquals(Const.USER_SERVICE_NAME, serviceRelation.getSourceServiceName());
        Assertions.assertEquals(service.getName(), serviceRelation.getDestServiceName());
        Assertions.assertEquals(Const.USER_INSTANCE_NAME, serviceInstanceRelation.getSourceServiceInstanceName());
        Assertions.assertEquals(serviceInstance.getName(), serviceInstanceRelation.getDestServiceInstanceName());
        Assertions.assertEquals(Const.USER_ENDPOINT_NAME, endpointRelation.getEndpoint());
        Assertions.assertEquals(endpoint.getName(), endpointRelation.getChildEndpoint());
    }

    /**
     * Entry span with ref, meaning the downstream has been instrumented.
     */
    @Test
    public void testEntrySpanRef() {
        final MockReceiver mockReceiver = new MockReceiver();
        RPCAnalysisListener listener = new RPCAnalysisListener(
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
        Assertions.assertEquals(6, receivedSources.size());
        final Service service = (Service) receivedSources.get(0);
        final ServiceInstance serviceInstance = (ServiceInstance) receivedSources.get(1);
        final ServiceRelation serviceRelation = (ServiceRelation) receivedSources.get(2);
        final ServiceInstanceRelation serviceInstanceRelation = (ServiceInstanceRelation) receivedSources.get(3);
        final Endpoint endpoint = (Endpoint) receivedSources.get(4);
        final EndpointRelation endpointRelation = (EndpointRelation) receivedSources.get(5);
        Assertions.assertEquals("mock-service", service.getName());
        Assertions.assertEquals("mock-instance", serviceInstance.getName());
        Assertions.assertEquals("/springMVC", endpoint.getName());
        Assertions.assertEquals("downstream-service", serviceRelation.getSourceServiceName());
        Assertions.assertEquals(service.getName(), serviceRelation.getDestServiceName());
        Assertions.assertEquals("downstream-instance", serviceInstanceRelation.getSourceServiceInstanceName());
        Assertions.assertEquals(serviceInstance.getName(), serviceInstanceRelation.getDestServiceInstanceName());
        Assertions.assertEquals("downstream-endpoint", endpointRelation.getEndpoint());
        Assertions.assertEquals(endpoint.getName(), endpointRelation.getChildEndpoint());
        // tags test
        Assertions.assertEquals("http.method:GET", service.getTags().get(0));
        Assertions.assertEquals("http.method:GET", serviceInstance.getTags().get(0));
        Assertions.assertEquals("http.method:GET", endpoint.getTags().get(0));
    }

    /**
     * Entry span with ref, but as a MQ server, or uninstrumented server.
     */
    @Test
    public void testEntrySpanMQRef() {
        final MockReceiver mockReceiver = new MockReceiver();
        RPCAnalysisListener listener = new RPCAnalysisListener(
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
        Assertions.assertEquals(6, receivedSources.size());
        final Service service = (Service) receivedSources.get(0);
        final ServiceInstance serviceInstance = (ServiceInstance) receivedSources.get(1);
        final ServiceRelation serviceRelation = (ServiceRelation) receivedSources.get(2);
        final ServiceInstanceRelation serviceInstanceRelation = (ServiceInstanceRelation) receivedSources.get(3);
        final Endpoint endpoint = (Endpoint) receivedSources.get(4);
        final EndpointRelation endpointRelation = (EndpointRelation) receivedSources.get(5);
        Assertions.assertEquals("mock-service", service.getName());
        Assertions.assertEquals("mock-instance", serviceInstance.getName());
        Assertions.assertEquals("/springMVC", endpoint.getName());
        Assertions.assertEquals("127.0.0.1", serviceRelation.getSourceServiceName());
        Assertions.assertEquals(service.getName(), serviceRelation.getDestServiceName());
        Assertions.assertEquals("127.0.0.1", serviceInstanceRelation.getSourceServiceInstanceName());
        Assertions.assertEquals(serviceInstance.getName(), serviceInstanceRelation.getDestServiceInstanceName());
        Assertions.assertEquals("downstream-endpoint", endpointRelation.getEndpoint());
        Assertions.assertEquals("downstream-service", endpointRelation.getServiceName());
        Assertions.assertEquals(endpoint.getName(), endpointRelation.getChildEndpoint());
    }

    /**
     * Local span analysis is triggered with logic span tag.
     */
    @Test
    public void testParseLocalLogicSpan() {
        final MockReceiver mockReceiver = new MockReceiver();
        RPCAnalysisListener listener = new RPCAnalysisListener(
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
        Assertions.assertEquals(1, receivedSources.size());
        final Endpoint source = (Endpoint) receivedSources.get(0);
        Assertions.assertEquals("/logic-call", source.getName());

        mockReceiver.clear();
    }

    /**
     * Local span analysis is triggered with extension logic service tags.
     */
    @Test
    public void testParseSpanWithLogicEndpointTag() {
        final MockReceiver mockReceiver = new MockReceiver();
        RPCAnalysisListener listener = new RPCAnalysisListener(
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
        Assertions.assertEquals(1, receivedSources.size());
        final Endpoint source = (Endpoint) receivedSources.get(0);
        Assertions.assertEquals("/GraphQL-service", source.getName());

        mockReceiver.clear();
    }

    /**
     * Exit span, represent calling a 3rd party system, when the alias has not been setup, including access database.
     */
    @Test
    public void testExitSpanWithoutAlias() {
        final MockReceiver mockReceiver = new MockReceiver();
        RPCAnalysisListener listener = new RPCAnalysisListener(
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
        Assertions.assertEquals(2, receivedSources.size());
        final ServiceRelation serviceRelation = (ServiceRelation) receivedSources.get(0);
        final ServiceInstanceRelation serviceInstanceRelation = (ServiceInstanceRelation) receivedSources.get(1);
        Assertions.assertEquals("mock-service", serviceRelation.getSourceServiceName());
        Assertions.assertEquals("127.0.0.1:8080", serviceRelation.getDestServiceName());
        Assertions.assertEquals("mock-instance", serviceInstanceRelation.getSourceServiceInstanceName());
        Assertions.assertEquals("127.0.0.1:8080", serviceInstanceRelation.getDestServiceInstanceName());
    }

    /**
     * Exit span, represent calling a 3rd party system, when the alias has been setup.
     */
    @Test
    public void testExitSpanWithAlias() {
        final MockReceiver mockReceiver = new MockReceiver();
        RPCAnalysisListener listener = new RPCAnalysisListener(
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
        Assertions.assertEquals(2, receivedSources.size());
        final ServiceRelation serviceRelation = (ServiceRelation) receivedSources.get(0);
        final ServiceInstanceRelation serviceInstanceRelation = (ServiceInstanceRelation) receivedSources.get(1);
        Assertions.assertEquals("mock-service", serviceRelation.getSourceServiceName());
        Assertions.assertEquals("target-service", serviceRelation.getDestServiceName());
        Assertions.assertEquals("mock-instance", serviceInstanceRelation.getSourceServiceInstanceName());
        Assertions.assertEquals("target-instance", serviceInstanceRelation.getDestServiceInstanceName());
        mockReceiver.clear();
    }

    @Test
    public void testMQEntryWithoutRef() {
        final MockReceiver mockReceiver = new MockReceiver();
        RPCAnalysisListener listener = new RPCAnalysisListener(
            mockReceiver,
            CONFIG,
            CACHE,
            NAMING_CONTROL
        );

        final long startTime = System.currentTimeMillis();
        SpanObject spanObject = SpanObject.newBuilder()
                                          .setOperationName("/MQ/consumer")
                                          .setStartTime(startTime)
                                          .setEndTime(startTime + 1000L)
                                          .setIsError(true)
                                          .setSpanType(SpanType.Entry)
                                          .setSpanLayer(SpanLayer.MQ)
                                          .setPeer("mq-server:9090")
                                          .addTags(
                                              KeyStringValuePair.newBuilder()
                                                                .setKey(SpanTags.MQ_QUEUE)
                                                                .setValue("queue")
                                                                .build()
                                          ).build();
        final SegmentObject segment = SegmentObject.newBuilder()
                                                   .setService("mock-service")
                                                   .setServiceInstance("mock-instance")
                                                   .addSpans(spanObject)
                                                   .build();
        listener.parseEntry(spanObject, segment);
        listener.build();

        final List<ISource> receivedSources = mockReceiver.getReceivedSources();
        Assertions.assertEquals(5, receivedSources.size());
        final Service service = (Service) receivedSources.get(0);
        final ServiceInstance serviceInstance = (ServiceInstance) receivedSources.get(1);
        final ServiceRelation serviceRelation = (ServiceRelation) receivedSources.get(2);
        final ServiceInstanceRelation serviceInstanceRelation = (ServiceInstanceRelation) receivedSources.get(3);
        final Endpoint endpoint = (Endpoint) receivedSources.get(4);
        Assertions.assertEquals("mock-service", service.getName());
        Assertions.assertEquals("/MQ/consumer", service.getEndpointName());
        Assertions.assertEquals(RequestType.MQ, service.getType());
        Assertions.assertFalse(service.isStatus());
        Assertions.assertEquals("mock-instance", serviceInstance.getName());
        Assertions.assertEquals("/MQ/consumer", endpoint.getName());
        Assertions.assertEquals("mq-server:9090", serviceRelation.getSourceServiceName());
        Assertions.assertEquals("mock-service", serviceRelation.getDestServiceName());
        Assertions.assertEquals("mq-server:9090", serviceInstanceRelation.getSourceServiceInstanceName());
        Assertions.assertEquals("mock-instance", serviceInstanceRelation.getDestServiceInstanceName());

    }

}
