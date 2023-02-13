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

package org.apache.skywalking.oap.server.analyzer.provider.meter.process;

import org.apache.skywalking.apm.network.language.agent.v3.MeterBucketValue;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfig;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfigs;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.StreamDefinition;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterEntity;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgHistogramFunction;
import org.apache.skywalking.oap.server.core.analysis.metrics.DataTable;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.config.group.EndpointNameGrouping;
import org.apache.skywalking.oap.server.core.query.type.Bucket;
import org.apache.skywalking.oap.server.core.storage.StorageException;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.powermock.reflect.Whitebox;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class MeterProcessorTest {

    @Mock
    private ModuleManager moduleManager;
    private MeterSystem meterSystem;
    private MeterProcessor processor;

    private String service = "test-service";
    private String serviceInstance = "test-service-instance";

    @BeforeAll
    public static void init() {
        MeterEntity.setNamingControl(
                new NamingControl(512, 512, 512, new EndpointNameGrouping()));
    }

    @BeforeEach
    public void setup() throws StorageException, ModuleStartException {
        meterSystem = spy(new MeterSystem(moduleManager));
        when(moduleManager.find(anyString())).thenReturn(mock(ModuleProviderHolder.class));
        when(moduleManager.find(CoreModule.NAME).provider()).thenReturn(mock(ModuleServiceHolder.class));
        when(moduleManager.find(CoreModule.NAME).provider().getService(MeterSystem.class)).thenReturn(meterSystem);
        Whitebox.setInternalState(MetricsStreamProcessor.class, "PROCESSOR",
                Mockito.spy(MetricsStreamProcessor.getInstance())
        );
        doNothing().when(MetricsStreamProcessor.getInstance()).create(any(), (StreamDefinition) any(), any());
        final MeterProcessService processService = new MeterProcessService(moduleManager);
        List<MeterConfig> config = MeterConfigs.loadConfig("meter-analyzer-config", Arrays.asList("config"));
        processService.start(config);
        processor = new MeterProcessor(processService);
    }

    @Test
    public void testProcess() {
        AtomicReference<AvgHistogramFunction> data = new AtomicReference<>();
        doAnswer(invocationOnMock -> {
            if (AvgHistogramFunction.class.isAssignableFrom(invocationOnMock.getArgument(0).getClass())) {
                data.set(invocationOnMock.getArgument(0));
            }
            return null;
        }).when(meterSystem).doStreamingCalculation(any());
        processor.read(MeterData.newBuilder()
                        .setService(service)
                        .setServiceInstance(serviceInstance)
                        .setTimestamp(System.currentTimeMillis())
                        .setHistogram(MeterHistogram.newBuilder()
                                .setName("test_histogram")
                                .addValues(MeterBucketValue.newBuilder().setIsNegativeInfinity(true).setCount(10).build())
                                .addValues(MeterBucketValue.newBuilder().setBucket(0).setCount(20).build())
                                .addValues(MeterBucketValue.newBuilder().setBucket(10).setCount(10).build())
                                .build())
                .build());
        processor.process();

        // verify data
        final AvgHistogramFunction func = data.get();
        final DataTable summation = new DataTable();
        summation.put(Bucket.INFINITE_NEGATIVE, 10L);
        summation.put("0", 20L);
        summation.put("10", 10L);
        Assertions.assertEquals(summation, func.getSummation());
        final DataTable count = new DataTable();
        count.put(Bucket.INFINITE_NEGATIVE, 1L);
        count.put("0", 1L);
        count.put("10", 1L);
        Assertions.assertEquals(count, func.getCount());
    }

}
