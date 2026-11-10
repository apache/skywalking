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

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.configuration.api.ConfigurationModule;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.alarm.AlarmHttpDeliveryMetrics;
import org.apache.skywalking.oap.server.core.alarm.AlarmModule;
import org.apache.skywalking.oap.server.core.alarm.HttpAlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.provider.grpc.GRPCCallback;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ValueColumnMetadata;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.ModuleProvider;
import org.apache.skywalking.oap.server.library.module.ModuleProviderHolder;
import org.apache.skywalking.oap.server.library.module.ModuleServiceHolder;
import org.apache.skywalking.oap.server.telemetry.TelemetryModule;
import org.apache.skywalking.oap.server.telemetry.api.MetricsCreator;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.skywalking.oap.server.testing.util.ReflectUtil;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AlarmModuleProviderTest {

    private AlarmModuleProvider moduleProvider;

    @BeforeEach
    public void setUp() throws Exception {
        ServiceLoader<ModuleProvider> serviceLoader = ServiceLoader.load(ModuleProvider.class);
        Iterator<ModuleProvider> providerIterator = serviceLoader.iterator();

        assertTrue(providerIterator.hasNext());

        moduleProvider = (AlarmModuleProvider) providerIterator.next();

        moduleProvider.newConfigCreator();

        moduleProvider.prepare();
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            "service_percent", "testColumn", Column.ValueDataType.COMMON_VALUE, 0, Scope.Service.getScopeId());
        ValueColumnMetadata.INSTANCE.putIfAbsent(
            "endpoint_percent", "testColumn", Column.ValueDataType.COMMON_VALUE, 0, Scope.Endpoint.getScopeId());
    }

    @Test
    public void name() {
        assertEquals("default", moduleProvider.name());
    }

    @Test
    public void module() {
        assertEquals(AlarmModule.class, moduleProvider.module());
    }

    @Test
    public void notifyAfterCompleted() throws Exception {
        final MetricsCreatorNoop noop = new MetricsCreatorNoop();
        final ModuleManager manager = mock(ModuleManager.class);
        final ModuleProviderHolder telemetryHolder = mock(ModuleProviderHolder.class);
        final ModuleServiceHolder telemetryProvider = mock(ModuleServiceHolder.class);
        when(manager.find(TelemetryModule.NAME)).thenReturn(telemetryHolder);
        when(telemetryHolder.provider()).thenReturn(telemetryProvider);
        when(telemetryProvider.getService(MetricsCreator.class)).thenReturn(noop);
        moduleProvider.setManager(manager);

        NotifyHandler handler = mock(NotifyHandler.class);
        ReflectUtil.setInternalState(moduleProvider, "notifyHandler", handler);
        moduleProvider.notifyAfterCompleted();

        final ArgumentCaptor<AlarmHttpDeliveryMetrics> metricsCaptor =
            ArgumentCaptor.forClass(AlarmHttpDeliveryMetrics.class);
        verify(handler).setDeliveryMetrics(metricsCaptor.capture());
        assertInstanceOf(AlarmHttpDeliveryMetrics.class, metricsCaptor.getValue());
        assertSame(noop, ReflectUtil.getInternalState(metricsCaptor.getValue(), "metricsCreator"));
        verify(handler).init(any());
    }

    @Test
    public void deliveryMetricsAndHookTypesReachHttpHooks() throws Exception {
        final NotifyHandler handler = ReflectUtil.getInternalState(moduleProvider, "notifyHandler");
        final AlarmHttpDeliveryMetrics metrics = new AlarmHttpDeliveryMetrics(new MetricsCreatorNoop());
        handler.setDeliveryMetrics(metrics);

        final Map<String, String> expectedHookTypes = Map.of(
            "WebhookCallback", AlarmHooksType.webhook.name(),
            "SlackhookCallback", AlarmHooksType.slack.name(),
            "WechatHookCallback", AlarmHooksType.wechat.name(),
            "DingtalkHookCallback", AlarmHooksType.dingtalk.name(),
            "FeishuHookCallback", AlarmHooksType.feishu.name(),
            "WeLinkHookCallback", AlarmHooksType.welink.name(),
            "PagerDutyHookCallback", AlarmHooksType.pagerduty.name(),
            "DiscordHookCallback", AlarmHooksType.discord.name()
        );
        final List<HttpAlarmCallback> hooks = handler.buildCallbacks()
                                                     .stream()
                                                     .filter(HttpAlarmCallback.class::isInstance)
                                                     .map(HttpAlarmCallback.class::cast)
                                                     .collect(Collectors.toList());
        assertEquals(expectedHookTypes.size(), hooks.size());
        for (final HttpAlarmCallback hook : hooks) {
            final String name = hook.getClass().getSimpleName();
            assertTrue(expectedHookTypes.containsKey(name), name);
            assertSame(metrics, ReflectUtil.getInternalState(hook, "deliveryMetrics"), name);
            assertEquals(expectedHookTypes.get(name), ReflectUtil.getInternalState(hook, "hookType"), name);
        }
    }

    @Test
    public void deliveryMetricsAndDefaultTimeoutReachTheGrpcHook() throws Exception {
        final NotifyHandler handler = ReflectUtil.getInternalState(moduleProvider, "notifyHandler");
        final AlarmHttpDeliveryMetrics metrics = new AlarmHttpDeliveryMetrics(new MetricsCreatorNoop());
        handler.setDeliveryMetrics(metrics);

        final GRPCCallback grpcHook = handler.buildCallbacks()
                                             .stream()
                                             .filter(GRPCCallback.class::isInstance)
                                             .map(GRPCCallback.class::cast)
                                             .findFirst()
                                             .orElseThrow();
        assertSame(metrics, ReflectUtil.getInternalState(grpcHook, "deliveryMetrics"));
        assertEquals(
            AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS,
            (long) ReflectUtil.getInternalState(grpcHook, "deadlineSeconds")
        );
    }

    @Test
    public void requiredModules() {
        String[] modules = moduleProvider.requiredModules();
        assertArrayEquals(new String[] {
            CoreModule.NAME,
            ConfigurationModule.NAME,
            TelemetryModule.NAME
        }, modules);
    }

    @Test
    public void httpTimeoutFallsBackAndIsCapped() {
        assertEquals(Duration.ofSeconds(5), AlarmModuleProvider.httpTimeout(5));
        assertEquals(Duration.ofSeconds(12), AlarmModuleProvider.httpTimeout(0));
        assertEquals(Duration.ofSeconds(12), AlarmModuleProvider.httpTimeout(-3));
        assertEquals(
            Duration.ofSeconds(TimeUnit.NANOSECONDS.toSeconds(Long.MAX_VALUE)),
            AlarmModuleProvider.httpTimeout(Long.MAX_VALUE)
        );
    }

    @Test
    public void grpcTimeoutFallsBackAndIsCapped() {
        assertEquals(7L, AlarmModuleProvider.grpcTimeout(7));
        assertEquals(AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS, AlarmModuleProvider.grpcTimeout(0));
        assertEquals(AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS, AlarmModuleProvider.grpcTimeout(-3));
        assertEquals(
            TimeUnit.NANOSECONDS.toSeconds(Long.MAX_VALUE),
            AlarmModuleProvider.grpcTimeout(Long.MAX_VALUE)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    public void configuredHttpTimeoutReachesTheHttpHooks() throws Exception {
        final AlarmModuleConfig config = new AlarmModuleConfig();
        config.setHttpTimeout(3);
        ((ModuleProvider.ConfigCreator<AlarmModuleConfig>) moduleProvider.newConfigCreator()).onInitialized(config);
        moduleProvider.prepare();

        final NotifyHandler handler = ReflectUtil.getInternalState(moduleProvider, "notifyHandler");
        handler.setDeliveryMetrics(new AlarmHttpDeliveryMetrics(new MetricsCreatorNoop()));
        final HttpClient client = ReflectUtil.getInternalState(handler, "httpClient");
        assertEquals(Duration.ofSeconds(3), client.connectTimeout().orElseThrow());
        final List<HttpAlarmCallback> hooks = handler.buildCallbacks()
                                                     .stream()
                                                     .filter(HttpAlarmCallback.class::isInstance)
                                                     .map(HttpAlarmCallback.class::cast)
                                                     .collect(Collectors.toList());
        assertEquals(8, hooks.size());
        for (final HttpAlarmCallback hook : hooks) {
            final String name = hook.getClass().getSimpleName();
            assertSame(client, ReflectUtil.getInternalState(hook, "httpClient"), name);
            assertEquals(Duration.ofSeconds(3), ReflectUtil.getInternalState(hook, "timeout"), name);
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void configuredGrpcTimeoutReachesTheGrpcHook() throws Exception {
        final AlarmModuleConfig config = new AlarmModuleConfig();
        config.setGrpcTimeout(3);
        ((ModuleProvider.ConfigCreator<AlarmModuleConfig>) moduleProvider.newConfigCreator()).onInitialized(config);
        moduleProvider.prepare();

        final NotifyHandler handler = ReflectUtil.getInternalState(moduleProvider, "notifyHandler");
        handler.setDeliveryMetrics(new AlarmHttpDeliveryMetrics(new MetricsCreatorNoop()));
        final GRPCCallback grpcHook = handler.buildCallbacks()
                                             .stream()
                                             .filter(GRPCCallback.class::isInstance)
                                             .map(GRPCCallback.class::cast)
                                             .findFirst()
                                             .orElseThrow();
        assertEquals(3L, (long) ReflectUtil.getInternalState(grpcHook, "deadlineSeconds"));
    }
}
