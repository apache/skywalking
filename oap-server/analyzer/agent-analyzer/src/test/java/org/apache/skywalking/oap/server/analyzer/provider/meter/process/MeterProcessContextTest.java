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

import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfig;
import org.apache.skywalking.oap.server.analyzer.provider.meter.config.MeterConfigs;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*", "org.w3c.*"})
public class MeterProcessContextTest extends MeterBaseTest {
    private static final String CONFIG_PATH = "meter-analyzer-config";

    @Test
    public void testInitMeter() throws ModuleStartException {
        List<MeterConfig> meterConfigs = MeterConfigs.loadConfig(CONFIG_PATH, new String[] {"config.yaml"});
        final MeterProcessService service = new MeterProcessService(moduleManager);
        service.start(meterConfigs);

        service.initMeters();
        verify(meterSystem, times(3)).create(any(), any(), any());
        Assert.assertEquals(3, service.enabledBuilders().size());
    }

    @Test
    public void testCreateNewProcessor() throws ModuleStartException {
        List<MeterConfig> meterConfigs = MeterConfigs.loadConfig(CONFIG_PATH, new String[] {"config.yaml"});
        final MeterProcessService service = new MeterProcessService(moduleManager);
        service.start(meterConfigs);

        final MeterProcessor processor = service.createProcessor();
        Assert.assertEquals(service, Whitebox.getInternalState(processor, "processService"));
    }
}
