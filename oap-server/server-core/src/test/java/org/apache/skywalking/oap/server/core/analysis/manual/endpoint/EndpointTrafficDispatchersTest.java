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

package org.apache.skywalking.oap.server.core.analysis.manual.endpoint;

import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.source.CiliumEndpoint;
import org.apache.skywalking.oap.server.core.source.EndpointMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.mockito.Mockito.verify;

/**
 * {@code findEndpoint} with a duration filters on {@link EndpointTraffic#LAST_PING_TIME_BUCKET}, so every
 * dispatcher writing {@link EndpointTraffic} must set it.
 */
public class EndpointTrafficDispatchersTest {
    private static final long TIME_BUCKET = 202610071230L;

    private MockedStatic<MetricsStreamProcessor> mockedProcessor;
    private MetricsStreamProcessor processorMock;

    @BeforeEach
    public void setup() {
        processorMock = Mockito.mock(MetricsStreamProcessor.class);
        mockedProcessor = Mockito.mockStatic(MetricsStreamProcessor.class);
        mockedProcessor.when(MetricsStreamProcessor::getInstance).thenReturn(processorMock);
    }

    @AfterEach
    public void tearDown() {
        mockedProcessor.close();
    }

    @Test
    public void testEndpointMetaSetsLastPing() {
        final EndpointMeta source = new EndpointMeta();
        source.setServiceName("repro-svc");
        source.setServiceNormal(true);
        source.setEndpoint("/repro/endpoint");
        source.setTimeBucket(TIME_BUCKET);
        source.prepare();

        new EndpointMetaDispatcher().dispatch(source);

        final EndpointTraffic traffic = captureTraffic();
        Assertions.assertEquals("/repro/endpoint", traffic.getName());
        Assertions.assertEquals(TIME_BUCKET, traffic.getLastPingTimestamp());
    }

    @Test
    public void testHubbleEndpointSetsLastPing() {
        final CiliumEndpoint source = new CiliumEndpoint();
        source.setServiceName("cilium-svc");
        source.setLayer(Layer.CILIUM_SERVICE);
        source.setEndpointName("/cilium/endpoint");
        source.setTimeBucket(TIME_BUCKET);
        source.prepare();

        new HubbleEndpointDispatcher().dispatch(source);

        final EndpointTraffic traffic = captureTraffic();
        Assertions.assertEquals("/cilium/endpoint", traffic.getName());
        Assertions.assertEquals(TIME_BUCKET, traffic.getLastPingTimestamp());
    }

    private EndpointTraffic captureTraffic() {
        final ArgumentCaptor<Metrics> captor = ArgumentCaptor.forClass(Metrics.class);
        verify(processorMock).in(captor.capture());
        return (EndpointTraffic) captor.getValue();
    }
}
