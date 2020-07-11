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

package org.apache.skywalking.oap.server.receiver.meter.provider.process;

import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.apache.skywalking.oap.server.receiver.meter.provider.MeterReceiverConfig;
import org.apache.skywalking.oap.server.receiver.meter.provider.config.MeterConfig;
import org.apache.skywalking.oap.server.receiver.meter.provider.config.MeterConfigs;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.internal.util.reflection.Whitebox;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.List;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
public class MeterProcessContextTest extends MeterBaseTest {

    @Test
    public void testInitMeter() throws ModuleStartException {
        List<MeterConfig> meterConfigs = MeterConfigs.loadConfig(new MeterReceiverConfig().getConfigPath());
        final MeterProcessContext context = new MeterProcessContext(meterConfigs, moduleManager);

        context.initMeters();
        verify(meterSystem, times(3)).create(any(), any(), any());
        Assert.assertEquals(3, context.enabledBuilders().size());
    }

    @Test
    public void testCreateNewProcessor() throws ModuleStartException {
        List<MeterConfig> meterConfigs = MeterConfigs.loadConfig(new MeterReceiverConfig().getConfigPath());
        final MeterProcessContext context = new MeterProcessContext(meterConfigs, moduleManager);

        final MeterProcessor processor = context.createProcessor();
        Assert.assertEquals(context, Whitebox.getInternalState(processor, "context"));
    }
}
