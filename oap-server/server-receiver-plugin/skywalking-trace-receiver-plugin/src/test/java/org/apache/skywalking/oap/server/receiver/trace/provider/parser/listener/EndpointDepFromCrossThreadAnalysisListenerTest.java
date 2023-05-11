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
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.apm.network.language.agent.v3.RefType;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentObject;
import org.apache.skywalking.apm.network.language.agent.v3.SegmentReference;
import org.apache.skywalking.apm.network.language.agent.v3.SpanLayer;
import org.apache.skywalking.apm.network.language.agent.v3.SpanObject;
import org.apache.skywalking.apm.network.language.agent.v3.SpanType;
import org.apache.skywalking.oap.server.analyzer.provider.AnalyzerModuleConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.UninstrumentedGatewaysConfig;
import org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener.EndpointDepFromCrossThreadAnalysisListener;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.Endpoint;
import org.apache.skywalking.oap.server.core.source.EndpointMeta;
import org.apache.skywalking.oap.server.core.source.EndpointRelation;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class EndpointDepFromCrossThreadAnalysisListenerTest {
    @Mock
    private static AnalyzerModuleConfig CONFIG;
    private static NamingControl NAMING_CONTROL = new NamingControl(
        70,
        100,
        100,
        new EndpointNameGrouping()
    );
    private final String serviceId = IDManager.ServiceID.buildId("local-service", true);
    private final String instanceId = IDManager.ServiceInstanceID.buildId(serviceId, "local-instance");

    @BeforeEach
    public void init() throws Exception {
        MockitoAnnotations.openMocks(this).close();

        final UninstrumentedGatewaysConfig uninstrumentedGatewaysConfig = Mockito.mock(
            UninstrumentedGatewaysConfig.class);
        when(uninstrumentedGatewaysConfig.isAddressConfiguredAsGateway(any())).thenReturn(false);
        when(CONFIG.getUninstrumentedGatewaysConfig()).thenReturn(uninstrumentedGatewaysConfig);
    }

    @Test
    public void testEndpointDependency() {
        final MockReceiver mockReceiver = new MockReceiver();
        EndpointDepFromCrossThreadAnalysisListener listener = new EndpointDepFromCrossThreadAnalysisListener(
            mockReceiver,
            CONFIG,
            NAMING_CONTROL
        );

        final long startTime = System.currentTimeMillis();
        SpanObject spanObject = SpanObject.newBuilder()
                                          .setOperationName("/local.method")
                                          .setStartTime(startTime)
                                          .setEndTime(startTime + 1000L)
                                          .setIsError(true)
                                          .setSpanType(SpanType.Local)
                                          .setSpanLayer(SpanLayer.Unknown)
                                          .addTags(KeyStringValuePair.newBuilder()
                                                                     .setKey("param")
                                                                     .setValue("value")
                                                                     .build())
                                          .addRefs(
                                              SegmentReference.newBuilder()
                                                              .setRefType(RefType.CrossThread)
                                                              .setParentService("local-service")
                                                              .setParentServiceInstance("local-instance")
                                                              .setParentEndpoint("/local.parentMethod")
                                                              .build()
                                          ).setComponentId(10)
                                          .build();
        final SegmentObject segment = SegmentObject.newBuilder()
                                                   .setService("local-service")
                                                   .setServiceInstance("local-instance")
                                                   .addSpans(spanObject)
                                                   .build();
        listener.parseLocal(spanObject, segment);
        listener.build();
        final List<ISource> receivedSources = mockReceiver.getReceivedSources();
        final EndpointMeta sourceEndpoint = (EndpointMeta) receivedSources.get(0);
        Assertions.assertEquals("local-service", sourceEndpoint.getServiceName());
        Assertions.assertEquals("/local.parentMethod", sourceEndpoint.getEndpoint());
        Assertions.assertTrue(sourceEndpoint.isServiceNormal());
        sourceEndpoint.prepare();
        Assertions.assertEquals(serviceId, sourceEndpoint.getServiceId());

        final Endpoint targetEndpoint = (Endpoint) receivedSources.get(1);
        Assertions.assertEquals("local-service", targetEndpoint.getServiceName());
        Assertions.assertEquals("/local.method", targetEndpoint.getName());
        Assertions.assertTrue(targetEndpoint.getServiceLayer().isNormal());
        targetEndpoint.prepare();
        Assertions.assertEquals(serviceId, targetEndpoint.getServiceId());

        final EndpointRelation endpointRelation = (EndpointRelation) receivedSources.get(2);
        Assertions.assertEquals("local-service", endpointRelation.getServiceName());
        Assertions.assertEquals("local-service", endpointRelation.getChildServiceName());
        Assertions.assertEquals("local-instance", endpointRelation.getServiceInstanceName());
        Assertions.assertEquals("local-instance", endpointRelation.getChildServiceInstanceName());
        Assertions.assertEquals("/local.parentMethod", endpointRelation.getEndpoint());
        Assertions.assertEquals("/local.method", endpointRelation.getChildEndpoint());
        Assertions.assertEquals(10, endpointRelation.getComponentId());
        Assertions.assertEquals(Layer.GENERAL, endpointRelation.getServiceLayer());
        Assertions.assertEquals(Layer.GENERAL, endpointRelation.getChildServiceLayer());
        Assertions.assertFalse(endpointRelation.isStatus());
        // No RPC/HTTP response code.
        Assertions.assertEquals(0, endpointRelation.getHttpResponseStatusCode());
        Assertions.assertEquals(null, endpointRelation.getRpcStatusCode());
    }
}
