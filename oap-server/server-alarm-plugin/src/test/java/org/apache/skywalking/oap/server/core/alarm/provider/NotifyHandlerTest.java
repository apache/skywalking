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
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.EndpointMetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.EndpointRelationMetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.MetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.ServiceInstanceMetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.ServiceInstanceRelationMetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.ServiceMetaInAlarm;
import org.apache.skywalking.oap.server.core.alarm.ServiceRelationMetaInAlarm;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.metrics.MetricsMetaInfo;
import org.apache.skywalking.oap.server.core.analysis.metrics.WithMetadata;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.powermock.reflect.Whitebox;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class NotifyHandlerTest {

    private NotifyHandler notifyHandler;

    private MockMetrics metrics;

    private MetricsMetaInfo metadata;

    private RunningRule rule;
    private MockedStatic<DefaultScopeDefine> defaultScopeDefineMockedStatic;

    @BeforeEach
    public void before() {
        metadata = mock(MetricsMetaInfo.class);
        when(metadata.getScope()).thenReturn(DefaultScopeDefine.ALL);
        when(metadata.getId()).thenReturn("");

        metrics = mock(MockMetrics.class);
        when(metrics.getMeta()).thenReturn(metadata);

        defaultScopeDefineMockedStatic = mockStatic(DefaultScopeDefine.class);
    }

    @AfterEach
    public void after() {
        defaultScopeDefineMockedStatic.close();
    }

    @Test
    public void testNotifyWithEndpointCatalog() {
        String metricsName = "endpoint-metrics";
        when(metadata.getMetricsName()).thenReturn(metricsName);

        when(DefaultScopeDefine.inEndpointCatalog(0)).thenReturn(true);

        String endpointInventoryName = "endpoint-inventory-name";

        String serviceInventoryName = "service-inventory-name";
        final String serviceId = IDManager.ServiceID.buildId(serviceInventoryName, true);
        final String endpointId = IDManager.EndpointID.buildId(serviceId, endpointInventoryName);
        when(metadata.getId()).thenReturn(endpointId);

        ArgumentCaptor<MetaInAlarm> metaCaptor = ArgumentCaptor.forClass(MetaInAlarm.class);

        notifyHandler.notify(metrics);
        verify(rule).in(metaCaptor.capture(), any());

        MetaInAlarm metaInAlarm = metaCaptor.getValue();

        assertTrue(metaInAlarm instanceof EndpointMetaInAlarm);
        assertEquals("c2VydmljZS1pbnZlbnRvcnktbmFtZQ==.1_ZW5kcG9pbnQtaW52ZW50b3J5LW5hbWU=", metaInAlarm.getId0());
        assertEquals(DefaultScopeDefine.ENDPOINT_CATALOG_NAME, metaInAlarm.getScope());
        assertEquals(endpointInventoryName + " in " + serviceInventoryName, metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.ENDPOINT, metaInAlarm.getScopeId());

    }

    @Test
    public void testNotifyWithServiceInstanceCatalog() {
        String metricsName = "service-instance-metrics";
        when(metadata.getMetricsName()).thenReturn(metricsName);

        when(DefaultScopeDefine.inServiceInstanceCatalog(0)).thenReturn(true);

        String instanceInventoryName = "instance-inventory-name";
        final String serviceId = IDManager.ServiceID.buildId("service", true);
        final String instanceId = IDManager.ServiceInstanceID.buildId(serviceId, instanceInventoryName);
        when(metadata.getId()).thenReturn(instanceId);

        ArgumentCaptor<MetaInAlarm> metaCaptor = ArgumentCaptor.forClass(MetaInAlarm.class);

        notifyHandler.notify(metrics);
        verify(rule).in(metaCaptor.capture(), any());

        MetaInAlarm metaInAlarm = metaCaptor.getValue();

        assertTrue(metaInAlarm instanceof ServiceInstanceMetaInAlarm);
        assertEquals("c2VydmljZQ==.1_aW5zdGFuY2UtaW52ZW50b3J5LW5hbWU=", metaInAlarm.getId0());
        assertEquals(DefaultScopeDefine.SERVICE_INSTANCE_CATALOG_NAME, metaInAlarm.getScope());
        assertEquals("instance-inventory-name of service", metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.SERVICE_INSTANCE, metaInAlarm.getScopeId());
    }

    @Test
    public void testNotifyWithServiceCatalog() {
        String metricsName = "service-metrics";
        when(metadata.getMetricsName()).thenReturn(metricsName);
        when(DefaultScopeDefine.inServiceCatalog(0)).thenReturn(true);
        final String serviceId = IDManager.ServiceID.buildId("service", true);
        when(metadata.getId()).thenReturn(serviceId);

        ArgumentCaptor<MetaInAlarm> metaCaptor = ArgumentCaptor.forClass(MetaInAlarm.class);

        notifyHandler.notify(metrics);
        verify(rule).in(metaCaptor.capture(), any());

        MetaInAlarm metaInAlarm = metaCaptor.getValue();

        assertTrue(metaInAlarm instanceof ServiceMetaInAlarm);
        assertEquals("c2VydmljZQ==.1", metaInAlarm.getId0());
        assertEquals(DefaultScopeDefine.SERVICE_CATALOG_NAME, metaInAlarm.getScope());
        assertEquals("service", metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.SERVICE, metaInAlarm.getScopeId());
    }

    @Test
    public void testNotifyWithServiceRelationCatalog() {
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
        assertEquals("ZnJvbS1zZXJ2aWNl.1", metaInAlarm.getId0());
        assertEquals("ZGVzdC1zZXJ2aWNl.1", metaInAlarm.getId1());
        assertEquals(DefaultScopeDefine.SERVICE_RELATION_CATALOG_NAME, metaInAlarm.getScope());
        assertEquals("from-service to dest-service", metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.SERVICE_RELATION, metaInAlarm.getScopeId());
    }

    @Test
    public void testNotifyWithServiceInstanceRelationCatalog() {
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
        assertEquals("ZnJvbS1zZXJ2aWNl.1_ZnJvbS1zZXJ2aWNlLWluc3RhbmNl", metaInAlarm.getId0());
        assertEquals("ZGVzdC1zZXJ2aWNl.1_ZGVzdC1zZXJ2aWNlLWluc3RhbmNl", metaInAlarm.getId1());
        assertEquals(DefaultScopeDefine.SERVICE_INSTANCE_RELATION_CATALOG_NAME, metaInAlarm.getScope());
        assertEquals("from-service-instance of from-service to dest-service-instance of dest-service", metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.SERVICE_INSTANCE_RELATION, metaInAlarm.getScopeId());
    }

    @Test
    public void testNotifyWithEndpointRelationCatalog() {
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
        assertEquals("ZnJvbS1zZXJ2aWNl.1_L3NvdXJjZS1wYXRo", metaInAlarm.getId0());
        assertEquals("ZGVzdC1zZXJ2aWNl.1_L2Rlc3QtcGF0aA==", metaInAlarm.getId1());
        assertEquals(DefaultScopeDefine.ENDPOINT_RELATION_CATALOG_NAME, metaInAlarm.getScope());
        assertEquals("/source-path in from-service to /dest-path in dest-service", metaInAlarm.getName());
        assertEquals(DefaultScopeDefine.ENDPOINT_RELATION, metaInAlarm.getScopeId());
    }

    @Test
    public void dontNotify() {

        MetricsMetaInfo metadata = mock(MetricsMetaInfo.class);
        when(metadata.getScope()).thenReturn(DefaultScopeDefine.SERVICE);

        MockMetrics mockMetrics = mock(MockMetrics.class);
        when(mockMetrics.getMeta()).thenReturn(metadata);

        notifyHandler.notify(mockMetrics);
    }

    @BeforeEach
    public void setUp() {

        Rules rules = new Rules();

        ModuleManager moduleManager = mock(ModuleManager.class);

        notifyHandler = new NotifyHandler(new AlarmRulesWatcher(rules, null, moduleManager), moduleManager);

        notifyHandler.init(new AlarmCallback() {
            @Override
            public void doAlarm(List<AlarmMessage> alarmMessages) throws Exception {
                    for (AlarmMessage message : alarmMessages) {
                        assertNotNull(message);
                    }
            }

            @Override
            public void doAlarmRecovery(List<AlarmMessage> alarmResolvedMessages) throws Exception {
                for (AlarmMessage message : alarmResolvedMessages) {
                    assertNotNull(message);
                }
            }
        }
    );

        AlarmCore core = mock(AlarmCore.class);

        rule = mock(RunningRule.class);

        doNothing().when(rule).in(any(MetaInAlarm.class), any(Metrics.class));

        when(core.findRunningRule(anyString())).thenReturn(Lists.newArrayList(rule));

        Whitebox.setInternalState(notifyHandler, "core", core);
    }

    public abstract static class MockMetrics extends Metrics implements WithMetadata {

    }
}
