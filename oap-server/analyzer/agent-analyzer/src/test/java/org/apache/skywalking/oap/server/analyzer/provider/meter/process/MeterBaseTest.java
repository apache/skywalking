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

import com.google.common.collect.Maps;
import org.apache.skywalking.apm.network.language.agent.v3.Label;
import org.apache.skywalking.apm.network.language.agent.v3.MeterBucketValue;
import org.apache.skywalking.apm.network.language.agent.v3.MeterData;
import org.apache.skywalking.apm.network.language.agent.v3.MeterHistogram;
import org.apache.skywalking.apm.network.language.agent.v3.MeterSingleValue;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfig;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfigs;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.CoreModuleProvider;
import org.apache.skywalking.oap.server.core.analysis.DisableRegister;
import org.apache.skywalking.oap.server.core.analysis.meter.MeterSystem;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgHistogramFunction;
import org.apache.skywalking.oap.server.core.analysis.meter.function.avg.AvgHistogramPercentileFunction;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.junit.Before;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

import java.util.HashMap;
import java.util.List;

import static org.mockito.Mockito.when;

public abstract class MeterBaseTest {
    private static final String CONFIG_PATH = "meter-analyzer-config";

    @Mock
    protected CoreModuleProvider moduleProvider;
    @Mock
    protected ModuleManager moduleManager;

    protected MeterSystem meterSystem;
    protected MeterProcessor processor;
    protected long timestamp;

    @Before
    public void init() throws Exception {
        // prepare the context
        meterSystem = Mockito.spy(new MeterSystem(moduleManager));
        CoreModule coreModule = Mockito.spy(CoreModule.class);

        // disable meter register
        DisableRegister.INSTANCE.add("meter_build_test1");
        DisableRegister.INSTANCE.add("meter_build_test2");
        DisableRegister.INSTANCE.add("meter_build_test3");

        Whitebox.setInternalState(coreModule, "loadedProvider", moduleProvider);
        when(moduleManager.find(CoreModule.NAME)).thenReturn(coreModule);

        when(moduleProvider.getService(MeterSystem.class))
            .thenReturn(meterSystem);

        // prepare the meter functions
        final HashMap<String, Class> map = Maps.newHashMap();
        map.put("avg", AvgFunction.class);
        map.put("avgHistogram", AvgHistogramFunction.class);
        map.put("avgHistogramPercentile", AvgHistogramPercentileFunction.class);
        Whitebox.setInternalState(meterSystem, "functionRegister", map);

        // load context
        List<MeterConfig> meterConfigs = MeterConfigs.loadConfig(CONFIG_PATH, new String[] {"config.yaml"});
        final MeterProcessService service = new MeterProcessService(moduleManager);
        service.start(meterConfigs);

        // create process and read meters
        processor = service.createProcessor();

        timestamp = System.currentTimeMillis();
        // single value
        processor.read(MeterData.newBuilder()
                                .setService("service").setServiceInstance("instance").setTimestamp(timestamp)
                                .setSingleValue(MeterSingleValue.newBuilder().setName("test_count1")
                                                                .addLabels(Label.newBuilder()
                                                                                .setName("k1")
                                                                                .setValue("v1")
                                                                                .build()).setValue(1).build())
                                .build());

        // histogram
        processor.read(MeterData.newBuilder()
                                .setHistogram(MeterHistogram.newBuilder().setName("test_histogram")
                                                            .addLabels(
                                                                Label.newBuilder().setName("k2").setValue("v2").build())
                                                            .addLabels(
                                                                Label.newBuilder().setName("endpoint").setValue("test_endpoint").build())
                                                            .addValues(MeterBucketValue.newBuilder()
                                                                                       .setBucket(1)
                                                                                       .setCount(10)
                                                                                       .build())
                                                            .addValues(MeterBucketValue.newBuilder()
                                                                                       .setBucket(5)
                                                                                       .setCount(15)
                                                                                       .build())
                                                            .addValues(MeterBucketValue.newBuilder()
                                                                                       .setBucket(10)
                                                                                       .setCount(3)
                                                                                       .build())
                                                            .build())
                                .build());
    }
}
