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
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.EndpointRelationMetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.ServiceInstanceRelationMetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.ServiceRelationMetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.EndpointMetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.MetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.ServiceInstanceMetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.ServiceMetaInAlarm;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.manual.endpoint.EndpointTraffic;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo;
import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.analysis.NodeType;
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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(PowerMockRunner.class)
@PowerMockIgnore({"com.sun.org.apache.xerces.*", "javax.xml.*", "org.xml.*", "javax.management.*", "org.w3c.*"})
@PrepareForTest(DefaultScopeDefine.class)
public class NotifyHandlerTest {

    private NotifyHandler notifyHandler;

    private ModuleManager moduleManager;

    private ModuleProviderHolder moduleProviderHolder;

    private ModuleServiceHolder moduleServiceHolder;

    private MockMetrics metrics;

    private MetricsMetaInfo metadata;

    private RunningRule rule;

    @Test
    public void testNotifyWithEndpointCatalog() {
        prepareNotify();

        String metricsName = "endpoint-metrics";
        when(metadata.getMetricsName()).thenReturn(metricsName);

        when(DefaultScopeDefine.inEndpointCatalog(0)).thenReturn(true);

        String endpointInventoryName = "endpoint-inventory-name";
        EndpointTraffic endpointTraffic = mock(EndpointTraffic.class);
        when(endpointTraffic.getName()).thenReturn(endpointInventoryName);

        String serviceInventoryName = "service-inventory-name";
        final String serviceId = IDManager.ServiceID.buildId(serviceInventoryName, NodeType.Normal);
        final String endpointId = IDManager.EndpointID.buildId(serviceId, endpointInventoryName);
        when(metadata.getId()).thenReturn(endpointId);

        ArgumentCaptor<MetaInAlarm> metaCaptor = ArgumentCaptor.forClass(MetaInAlarm.class);

        notifyHandler.notify(metrics);
        verify(rule).in(metaCaptor.capture(), any());

        MetaInAlarm metaInAlarm = metaCaptor.getValue();

        assertTrue(metaInAlarm instanceof EndpointMetaInAlarm);
        assertEquals("c2VydmljZS1pbnZlbnRvcnktbmFtZQ==.1_ZW5kcG9pbnQtaW52ZW50b3J5LW5hbWU=", metaInAlarm.getId0());
        assertEquals(DefaultScopeDefine.ENDPOINT_CATALOG_NAME, metaInAlarm.getScope());
        assertEquals(metricsName, metaInAlarm.getMetricsName());
        assertEquals(endpointInventoryName + " in " + serviceInventoryName, metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.ENDPOINT, metaInAlarm.getScopeId());

    }

    @Test
    public void testNotifyWithServiceInstanceCatalog() {

        prepareNotify();

        String metricsName = "service-instance-metrics";
        when(metadata.getMetricsName()).thenReturn(metricsName);

        when(DefaultScopeDefine.inServiceInstanceCatalog(0)).thenReturn(true);

        String instanceInventoryName = "instance-inventory-name";
        final String serviceId = IDManager.ServiceID.buildId("service", NodeType.Normal);
        final String instanceId = IDManager.ServiceInstanceID.buildId(serviceId, instanceInventoryName);
        when(metadata.getId()).thenReturn(instanceId);

        ArgumentCaptor<MetaInAlarm> metaCaptor = ArgumentCaptor.forClass(MetaInAlarm.class);

        notifyHandler.notify(metrics);
        verify(rule).in(metaCaptor.capture(), any());

        MetaInAlarm metaInAlarm = metaCaptor.getValue();

        assertTrue(metaInAlarm instanceof ServiceInstanceMetaInAlarm);
        assertEquals(metricsName, metaInAlarm.getMetricsName());
        assertEquals("c2VydmljZQ==.1_aW5zdGFuY2UtaW52ZW50b3J5LW5hbWU=", metaInAlarm.getId0());
        assertEquals(DefaultScopeDefine.SERVICE_INSTANCE_CATALOG_NAME, metaInAlarm.getScope());
        assertEquals("instance-inventory-name of service", metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.SERVICE_INSTANCE, metaInAlarm.getScopeId());
    }

    @Test
    public void testNotifyWithServiceCatalog() {
        prepareNotify();

        String metricsName = "service-metrics";
        when(metadata.getMetricsName()).thenReturn(metricsName);
        when(DefaultScopeDefine.inServiceCatalog(0)).thenReturn(true);
        final String serviceId = IDManager.ServiceID.buildId("service", NodeType.Normal);
        when(metadata.getId()).thenReturn(serviceId);

        ArgumentCaptor<MetaInAlarm> metaCaptor = ArgumentCaptor.forClass(MetaInAlarm.class);

        notifyHandler.notify(metrics);
        verify(rule).in(metaCaptor.capture(), any());

        MetaInAlarm metaInAlarm = metaCaptor.getValue();

        assertTrue(metaInAlarm instanceof ServiceMetaInAlarm);
        assertEquals(metricsName, metaInAlarm.getMetricsName());
        assertEquals("c2VydmljZQ==.1", metaInAlarm.getId0());
        assertEquals(DefaultScopeDefine.SERVICE_CATALOG_NAME, metaInAlarm.getScope());
        assertEquals("service", metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.SERVICE, metaInAlarm.getScopeId());
    }

    @Test
    public void testNotifyWithServiceRelationCatalog() {
        prepareNotify();

        String metricsName = "service-relation-metrics";
        when(metadata.getMetricsName()).thenReturn(metricsName);
        when(DefaultScopeDefine.inServiceRelationCatalog(0)).thenReturn(true);
        final String serviceRelationId = IDManager.ServiceID.buildRelationId(new IDManager.ServiceID.ServiceRelationDefine(
            IDManager.ServiceID.buildId("from-service", true),
            IDManager.ServiceID.buildId("dest-service", true)
        ));
        when(metadata.getId()).thenReturn(serviceRelationId);

        ArgumentCaptor<MetaInAlarm> metaCaptor = ArgumentCaptor.forClass(MetaInAlarm.class);

        notifyHandler.notify(metrics);
        verify(rule).in(metaCaptor.capture(), any());

        MetaInAlarm metaInAlarm = metaCaptor.getValue();

        assertTrue(metaInAlarm instanceof ServiceRelationMetaInAlarm);
        assertEquals(metricsName, metaInAlarm.getMetricsName());
        assertEquals("ZnJvbS1zZXJ2aWNl.1", metaInAlarm.getId0());
        assertEquals("ZGVzdC1zZXJ2aWNl.1", metaInAlarm.getId1());
        assertEquals(DefaultScopeDefine.SERVICE_RELATION_CATALOG_NAME, metaInAlarm.getScope());
        assertEquals("from-service to dest-service", metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.SERVICE_RELATION, metaInAlarm.getScopeId());
    }

    @Test
    public void testNotifyWithServiceInstanceRelationCatalog() {
        prepareNotify();

        String metricsName = "service-instance-relation-metrics";
        when(metadata.getMetricsName()).thenReturn(metricsName);
        when(DefaultScopeDefine.inServiceInstanceRelationCatalog(0)).thenReturn(true);
        final String serviceInstanceRelationId = IDManager.ServiceInstanceID.buildRelationId(new IDManager.ServiceInstanceID.ServiceInstanceRelationDefine(
            IDManager.ServiceInstanceID.buildId(IDManager.ServiceID.buildId("from-service", true), "from-service-instance"),
            IDManager.ServiceInstanceID.buildId(IDManager.ServiceID.buildId("dest-service", true), "dest-service-instance")
        ));
        when(metadata.getId()).thenReturn(serviceInstanceRelationId);

        ArgumentCaptor<MetaInAlarm> metaCaptor = ArgumentCaptor.forClass(MetaInAlarm.class);

        notifyHandler.notify(metrics);
        verify(rule).in(metaCaptor.capture(), any());

        MetaInAlarm metaInAlarm = metaCaptor.getValue();

        assertTrue(metaInAlarm instanceof ServiceInstanceRelationMetaInAlarm);
        assertEquals(metricsName, metaInAlarm.getMetricsName());
        assertEquals("ZnJvbS1zZXJ2aWNl.1_ZnJvbS1zZXJ2aWNlLWluc3RhbmNl", metaInAlarm.getId0());
        assertEquals("ZGVzdC1zZXJ2aWNl.1_ZGVzdC1zZXJ2aWNlLWluc3RhbmNl", metaInAlarm.getId1());
        assertEquals(DefaultScopeDefine.SERVICE_INSTANCE_RELATION_CATALOG_NAME, metaInAlarm.getScope());
        assertEquals("from-service-instance of from-service to dest-service-instance of dest-service", metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.SERVICE_INSTANCE_RELATION, metaInAlarm.getScopeId());
    }

    @Test
    public void testNotifyWithEndpointRelationCatalog() {
        prepareNotify();

        String metricsName = "endpoint-relation-metrics";
        when(metadata.getMetricsName()).thenReturn(metricsName);
        when(DefaultScopeDefine.inEndpointRelationCatalog(0)).thenReturn(true);
        final String serviceInstanceRelationId = IDManager.EndpointID.buildRelationId(new IDManager.EndpointID.EndpointRelationDefine(
            IDManager.ServiceID.buildId("from-service", true), "/source-path",
            IDManager.ServiceID.buildId("dest-service", true), "/dest-path"
        ));
        when(metadata.getId()).thenReturn(serviceInstanceRelationId);

        ArgumentCaptor<MetaInAlarm> metaCaptor = ArgumentCaptor.forClass(MetaInAlarm.class);

        notifyHandler.notify(metrics);
        verify(rule).in(metaCaptor.capture(), any());

        MetaInAlarm metaInAlarm = metaCaptor.getValue();

        assertTrue(metaInAlarm instanceof EndpointRelationMetaInAlarm);
        assertEquals(metricsName, metaInAlarm.getMetricsName());
        assertEquals("ZnJvbS1zZXJ2aWNl.1_L3NvdXJjZS1wYXRo", metaInAlarm.getId0());
        assertEquals("ZGVzdC1zZXJ2aWNl.1_L2Rlc3QtcGF0aA==", metaInAlarm.getId1());
        assertEquals(DefaultScopeDefine.ENDPOINT_RELATION_CATALOG_NAME, metaInAlarm.getScope());
        assertEquals("/source-path in from-service to /dest-path in dest-service", metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.ENDPOINT_RELATION, metaInAlarm.getScopeId());
    }

    private void prepareNotify() {
        metadata = mock(MetricsMetaInfo.class);
        when(metadata.getScope()).thenReturn(DefaultScopeDefine.ALL);
        when(metadata.getId()).thenReturn("");

        metrics = mock(MockMetrics.class);
        when(metrics.getMeta()).thenReturn(metadata);

        PowerMockito.mockStatic(DefaultScopeDefine.class);
    }

    @Test
    public void dontNotify() {

        MetricsMetaInfo metadata = mock(MetricsMetaInfo.class);
        when(metadata.getScope()).thenReturn(DefaultScopeDefine.ALL);

        MockMetrics mockMetrics = mock(MockMetrics.class);
        when(mockMetrics.getMeta()).thenReturn(metadata);

        notifyHandler.notify(mockMetrics);
    }

    @Before
    public void setUp() throws Exception {

        Rules rules = new Rules();

        notifyHandler = new NotifyHandler(new AlarmRulesWatcher(rules, null));

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

        AlarmCore core = mock(AlarmCore.class);

        rule = mock(RunningRule.class);

        doNothing().when(rule).in(any(MetaInAlarm.class), any(Metrics.class));

        when(core.findRunningRule(anyString())).thenReturn(Lists.newArrayList(rule));

        Whitebox.setInternalState(notifyHandler, "core", core);
    }

    private abstract class MockMetrics extends Metrics implements WithMetadata {

    }
}