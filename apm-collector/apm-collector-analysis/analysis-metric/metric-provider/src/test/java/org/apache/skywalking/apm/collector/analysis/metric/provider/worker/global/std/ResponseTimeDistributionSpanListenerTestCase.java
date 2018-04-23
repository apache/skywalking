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

package org.apache.skywalking.apm.collector.analysis.metric.provider.worker.global.std;

import java.util.LinkedList;
import org.apache.skywalking.apm.collector.configuration.ConfigurationModule;
import org.apache.skywalking.apm.collector.configuration.service.IResponseTimeDistributionConfigService;
import org.apache.skywalking.apm.collector.core.module.Module;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.module.ModuleProvider;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.powermock.reflect.Whitebox;

/**
 * @author peng-yongsheng
 */
public class ResponseTimeDistributionSpanListenerTestCase {

    @Test
    public void testStep() {
        ModuleProvider moduleProvider = Mockito.mock(ModuleProvider.class);
        LinkedList<ModuleProvider> loadedProviders = new LinkedList<>();
        loadedProviders.add(moduleProvider);

        IResponseTimeDistributionConfigService service = Mockito.mock(IResponseTimeDistributionConfigService.class);
        Mockito.when(service.getResponseTimeStep()).thenReturn(50);
        Mockito.when(service.getResponseTimeMaxStep()).thenReturn(40);

        Module module = Mockito.mock(Module.class);
        Whitebox.setInternalState(module, "loadedProviders", loadedProviders);
        Mockito.when(module.getService(IResponseTimeDistributionConfigService.class)).thenReturn(service);

        ModuleManager moduleManager = Mockito.mock(ModuleManager.class);
        Mockito.when(moduleManager.find(ConfigurationModule.NAME)).thenReturn(module);

        ResponseTimeDistributionSpanListener listener = new ResponseTimeDistributionSpanListener(moduleManager);

        Whitebox.setInternalState(listener, "entrySpanDuration", 0);
        Whitebox.setInternalState(listener, "firstSpanDuration", 200);
        Assert.assertEquals(3, listener.getStep());

        Whitebox.setInternalState(listener, "entrySpanDuration", 10);
        Assert.assertEquals(0, listener.getStep());

        Whitebox.setInternalState(listener, "entrySpanDuration", 60);
        Assert.assertEquals(1, listener.getStep());

        Whitebox.setInternalState(listener, "entrySpanDuration", 3100);
        Assert.assertEquals(40, listener.getStep());
    }
}
