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

package org.apache.skywalking.oap.server.analyzer.agent.kafka.provider.handler;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.utils.Bytes;
import org.apache.skywalking.apm.network.common.v3.CPU;
import org.apache.skywalking.apm.network.language.agent.v3.ClrGC;
import org.apache.skywalking.apm.network.language.agent.v3.CLRMetric;
import org.apache.skywalking.apm.network.language.agent.v3.CLRMetricCollection;
import org.apache.skywalking.apm.network.language.agent.v3.ClrThread;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.mock.MockModuleManager;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.mock.MockModuleProvider;
import org.apache.skywalking.oap.server.analyzer.agent.kafka.module.KafkaFetcherConfig;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.source.ISource;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceCLRCPU;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceCLRGC;
import org.apache.skywalking.oap.server.core.source.ServiceInstanceCLRThread;
import org.apache.skywalking.oap.server.core.source.SourceReceiver;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.util.List;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

public class CLRMetricsHandlerTest {
    private static final String TOPIC_NAME = "skywalking-clr-metrics";
    private CLRMetricsHandler handler = null;
    private KafkaFetcherConfig config = new KafkaFetcherConfig();

    private ModuleManager manager;

    @RegisterExtension
    public final SourceReceiverRule sourceReceiverRule = new SourceReceiverRule() {

        @Override
        protected void verify(final List<ISource> sourceList) {
            Assertions.assertTrue(sourceList.get(0) instanceof ServiceInstanceCLRCPU);
            ServiceInstanceCLRCPU serviceInstanceCLRCPU = (ServiceInstanceCLRCPU) sourceList.get(0);
            assertThat(serviceInstanceCLRCPU.getUsePercent()).isEqualTo(1.0);
            Assertions.assertTrue(sourceList.get(1) instanceof ServiceInstanceCLRGC);
            Assertions.assertTrue(sourceList.get(2) instanceof ServiceInstanceCLRThread);
        }
    };

    @BeforeEach
    public void setup() {
        manager = new MockModuleManager() {
            @Override
            protected void init() {
                register(CoreModule.NAME, () -> new MockModuleProvider() {
                    @Override
                    protected void register() {
                        registerServiceImplementation(NamingControl.class, new NamingControl(
                            512, 512, 512, new EndpointNameGrouping()));
                        registerServiceImplementation(SourceReceiver.class, sourceReceiverRule);
                    }
                });
                register(TelemetryModule.NAME, () -> new MockModuleProvider() {
                    @Override
                    protected void register() {
                        registerServiceImplementation(MetricsCreator.class, new MetricsCreatorNoop());
                    }
                });
            }
        };
        handler = new CLRMetricsHandler(manager, config);
    }

    @Test
    public void testTopicName() {
        Assertions.assertEquals(handler.getTopic(), TOPIC_NAME);
    }

    @Test
    public void testHandler() {
        long currentTimeMillis = System.currentTimeMillis();

        CLRMetric.Builder clrBuilder = CLRMetric.newBuilder();
        clrBuilder.setTime(currentTimeMillis);
        clrBuilder.setCpu(CPU.newBuilder().setUsagePercent(0.98d).build());
        clrBuilder.setGc(ClrGC.newBuilder().build());
        clrBuilder.setThread(ClrThread.newBuilder().build());

        CLRMetricCollection metrics = CLRMetricCollection.newBuilder()
                                                         .setService("service")
                                                         .setServiceInstance("service-instance")
                                                         .addMetrics(clrBuilder.build())
                                                         .build();

        handler.handle(new ConsumerRecord<>(TOPIC_NAME, 0, 0, "", Bytes.wrap(metrics.toByteArray())));
    }
}
