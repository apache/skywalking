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

package org.apache.skywalking.oap.server.core.alarm.provider;

import com.google.common.collect.Lists;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.alarm.*;
import org.apache.skywalking.oap.server.core.analysis.indicator.Indicator;
import org.apache.skywalking.oap.server.core.analysis.indicator.IndicatorMetaInfo;
import org.apache.skywalking.oap.server.core.analysis.indicator.WithMetadata;
import org.apache.skywalking.oap.server.core.cache.EndpointInventoryCache;
import org.apache.skywalking.oap.server.core.cache.ServiceInstanceInventoryCache;
import org.apache.skywalking.oap.server.core.cache.ServiceInventoryCache;
import org.apache.skywalking.oap.server.core.register.EndpointInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInstanceInventory;
import org.apache.skywalking.oap.server.core.register.ServiceInventory;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import static junit.framework.TestCase.*;
import static org.mockito.Mockito.*;

/**
 * Created by dengming, 2019.04.22
 */
@RunWith(PowerMockRunner.class)
@PowerMockIgnore("javax.management.*")
@PrepareForTest(DefaultScopeDefine.class)
public class NotifyHandlerTest {

    private NotifyHandler notifyHandler;

    private ModuleManager moduleManager;

    private ModuleProviderHolder moduleProviderHolder;

    private ModuleServiceHolder moduleServiceHolder;

    private ServiceInventoryCache serviceInventoryCache;

    private ServiceInstanceInventoryCache serviceInstanceInventoryCache;

    private EndpointInventoryCache endpointInventoryCache;

    private MockIndicator indicator;

    private IndicatorMetaInfo metadata;

    private int mockId = 1;

    private RunningRule rule;


    @Test
    public void testNotifyWithEndpointCatalog() {
        prepareNotify();

        String indicatorName = "endpoint-indicator";
        when(metadata.getIndicatorName()).thenReturn(indicatorName);

        when(DefaultScopeDefine.inEndpointCatalog(0)).thenReturn(true);

        String endpointInventoryName = "endpoint-inventory-name";
        EndpointInventory endpointInventory = mock(EndpointInventory.class);
        when(endpointInventory.getName()).thenReturn(endpointInventoryName);

        String serviceInventoryName = "service-inventory-name";
        ServiceInventory serviceInventory = mock(ServiceInventory.class);
        when(serviceInventory.getName()).thenReturn(serviceInventoryName);

        when(serviceInventoryCache.get(anyInt())).thenReturn(serviceInventory);

        when(endpointInventoryCache.get(anyInt())).thenReturn(endpointInventory);

        ArgumentCaptor<MetaInAlarm> metaCaptor = ArgumentCaptor.forClass(MetaInAlarm.class);

        notifyHandler.notify(indicator);
        verify(rule).in(metaCaptor.capture(), any());

        MetaInAlarm metaInAlarm = metaCaptor.getValue();

        assertTrue(metaInAlarm instanceof EndpointMetaInAlarm);
        assertEquals(mockId, metaInAlarm.getId0());
        assertEquals(indicatorName, metaInAlarm.getIndicatorName());
        assertEquals(endpointInventoryName + " in " + serviceInventoryName, metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.ENDPOINT, metaInAlarm.getScopeId());

    }

    @Test
    public void testNotifyWithServiceInstanceCatalog() {

        prepareNotify();

        String indicatorName = "service-instance-indicator";
        when(metadata.getIndicatorName()).thenReturn(indicatorName);

        when(DefaultScopeDefine.inServiceInstanceCatalog(0)).thenReturn(true);

        ServiceInstanceInventory instanceInventory = mock(ServiceInstanceInventory.class);
        String instanceInventoryName = "instance-inventory-name";
        when(instanceInventory.getName()).thenReturn(instanceInventoryName);

        when(serviceInstanceInventoryCache.get(anyInt())).thenReturn(instanceInventory);

        ArgumentCaptor<MetaInAlarm> metaCaptor = ArgumentCaptor.forClass(MetaInAlarm.class);

        notifyHandler.notify(indicator);
        verify(rule).in(metaCaptor.capture(), any());

        MetaInAlarm metaInAlarm = metaCaptor.getValue();

        assertTrue(metaInAlarm instanceof ServiceInstanceMetaInAlarm);
        assertEquals(indicatorName, metaInAlarm.getIndicatorName());
        assertEquals(mockId, metaInAlarm.getId0());
        assertEquals(instanceInventoryName, metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.SERVICE_INSTANCE, metaInAlarm.getScopeId());
    }

    @Test
    public void testNotifyWithServiceCatalog() {
        prepareNotify();

        String indicatorName = "service-indicator";
        when(metadata.getIndicatorName()).thenReturn(indicatorName);
        when(DefaultScopeDefine.inServiceCatalog(0)).thenReturn(true);

        ServiceInventory serviceInventory = mock(ServiceInventory.class);
        String serviceInventoryName = "service-inventory";
        when(serviceInventory.getName()).thenReturn(serviceInventoryName);

        when(serviceInventoryCache.get(anyInt())).thenReturn(serviceInventory);

        ArgumentCaptor<MetaInAlarm> metaCaptor = ArgumentCaptor.forClass(MetaInAlarm.class);

        notifyHandler.notify(indicator);
        verify(rule).in(metaCaptor.capture(), any());

        MetaInAlarm metaInAlarm = metaCaptor.getValue();

        assertTrue(metaInAlarm instanceof ServiceMetaInAlarm);
        assertEquals(indicatorName, metaInAlarm.getIndicatorName());
        assertEquals(mockId, metaInAlarm.getId0());
        assertEquals(serviceInventoryName, metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.SERVICE, metaInAlarm.getScopeId());
    }

    private void prepareNotify() {
        notifyHandler.initCache(moduleManager);

        metadata = mock(IndicatorMetaInfo.class);
        when(metadata.getScope()).thenReturn(DefaultScopeDefine.ALL);
        when(metadata.getId()).thenReturn(String.valueOf(mockId));

        indicator = mock(MockIndicator.class);
        when(indicator.getMeta()).thenReturn(metadata);

        PowerMockito.mockStatic(DefaultScopeDefine.class);
    }

    @Test
    public void dontNotify() {

        IndicatorMetaInfo metadata = mock(IndicatorMetaInfo.class);
        when(metadata.getScope()).thenReturn(DefaultScopeDefine.ALL);

        MockIndicator indicator = mock(MockIndicator.class);
        when(indicator.getMeta()).thenReturn(metadata);

        notifyHandler.notify(indicator);
    }

    @Test
    public void initCache() {

        notifyHandler.initCache(moduleManager);
    }


    @Before
    public void setUp() throws Exception {

        Rules rules = new Rules();

        notifyHandler = new NotifyHandler(rules);

        notifyHandler.init(alarmMessageList -> {
            for (AlarmMessage message : alarmMessageList) {
                assertNotNull(message);
            }
        });


        moduleManager = mock(ModuleManager.class);

        moduleProviderHolder = mock(ModuleProviderHolder.class);


        moduleServiceHolder = mock(ModuleServiceHolder.class);

        when(moduleManager.find(CoreModule.NAME)).thenReturn(moduleProviderHolder);
        when(moduleProviderHolder.provider()).thenReturn(moduleServiceHolder);

        serviceInventoryCache = mock(ServiceInventoryCache.class);
        serviceInstanceInventoryCache = mock(ServiceInstanceInventoryCache.class);
        endpointInventoryCache = mock(EndpointInventoryCache.class);

        when(moduleServiceHolder.getService(ServiceInventoryCache.class)).thenReturn(serviceInventoryCache);
        when(moduleServiceHolder.getService(ServiceInstanceInventoryCache.class)).thenReturn(serviceInstanceInventoryCache);
        when(moduleServiceHolder.getService(EndpointInventoryCache.class)).thenReturn(endpointInventoryCache);

        AlarmCore core = mock(AlarmCore.class);

        rule = mock(RunningRule.class);

        doNothing().when(rule).in(any(MetaInAlarm.class), any(Indicator.class));

        when(core.findRunningRule(anyString())).thenReturn(Lists.newArrayList(rule));

        Whitebox.setInternalState(notifyHandler, "core", core);
    }

    private abstract class MockIndicator extends Indicator implements WithMetadata {

    }
}