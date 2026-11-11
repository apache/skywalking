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

package org.apache.skywalking.oap.server.core.alarm.provider.grpc;

import com.google.common.collect.Lists;
import io.grpc.Server;
import io.grpc.netty.NettyServerBuilder;
import io.grpc.stub.StreamObserver;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.alarm.AlarmHttpDeliveryMetrics;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecoveryMessage;
import org.apache.skywalking.oap.server.core.alarm.grpc.AlarmServiceGrpc;
import org.apache.skywalking.oap.server.core.alarm.grpc.Response;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHooksType;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmModuleConfig;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.alarm.provider.Rules;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;
import org.apache.skywalking.oap.server.telemetry.api.CounterMetrics;
import org.apache.skywalking.oap.server.telemetry.api.HistogramMetrics;
import org.apache.skywalking.oap.server.telemetry.api.MetricsTag;
import org.apache.skywalking.oap.server.telemetry.none.MetricsCreatorNoop;
import org.apache.skywalking.oap.server.testing.util.ReflectUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class GRPChookCallbackTest {

    private GRPCCallback grpcCallback;

    // testGauchoSettingClean replaces grpcCallback, so keep the one init() built to shut it down.
    private GRPCCallback initCallback;

    private AlarmRulesWatcher alarmRulesWatcher;

    private List<AlarmMessage> alarmMessageList;
    private List<AlarmMessage> alarmRecoveryMessageList;

    @BeforeEach
    public void init() throws Exception {
        Rules rules = new Rules();
        GRPCAlarmSetting setting1 = new GRPCAlarmSetting("setting1", AlarmHooksType.gRPC, true);
        setting1.setTargetHost("127.0.0.1");
        setting1.setTargetPort(9888);
        GRPCAlarmSetting setting2 = new GRPCAlarmSetting("setting2", AlarmHooksType.gRPC, false);
        setting2.setTargetHost("127.0.0.1");
        setting2.setTargetPort(9888);
        rules.getGrpcAlarmSettingMap().put(setting1.getFormattedName(), setting1);
        rules.getGrpcAlarmSettingMap().put(setting2.getFormattedName(), setting2);

        alarmRulesWatcher = new AlarmRulesWatcher(rules, null, null);
        grpcCallback = new GRPCCallback(alarmRulesWatcher, noopMetrics(), AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS);
        initCallback = grpcCallback;
        mockAlarmMessage(setting1.getFormattedName(), setting2.getFormattedName());
        mockAlarmRecoveryMessage(setting1.getFormattedName(), setting2.getFormattedName());
    }

    @AfterEach
    public void shutdownInitClients() {
        ReflectUtil.<Map<String, GRPCClient>>getInternalState(initCallback, "grpcClientMap")
            .values()
            .forEach(GRPCClient::shutdown);
    }

    @Test
    public void doAlarm() {
        grpcCallback.doAlarm(alarmMessageList);
        grpcCallback.doAlarmRecovery(alarmRecoveryMessageList);
    }

    @Test
    public void testGauchoSettingClean() {
        Rules rules = new Rules();
        GRPCAlarmSetting setting1 = new GRPCAlarmSetting("setting1111111", AlarmHooksType.gRPC, true);
        GRPCAlarmSetting setting2 = new GRPCAlarmSetting("setting2222222", AlarmHooksType.gRPC, true);
        rules.getGrpcAlarmSettingMap().put(setting1.getFormattedName(), setting1);
        rules.getGrpcAlarmSettingMap().put(setting2.getFormattedName(), setting2);
        alarmRulesWatcher = new AlarmRulesWatcher(rules, null, null);
        grpcCallback = new GRPCCallback(alarmRulesWatcher, noopMetrics(), AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS);
        grpcCallback.doAlarm(alarmMessageList);
        grpcCallback.doAlarmRecovery(alarmRecoveryMessageList);
    }

    /**
     * Dynamic config clearing gRPC hooks used to null the settings and stub maps; restoring
     * targets then NPE'd on the next alarm. After restore, real alarm and recovery
     * messages must reach a live hook server — not only leave maps non-empty.
     */
    @Test
    public void shouldKeepMapsUsableAfterClearThenRestore() throws Exception {
        final RecordingAlarmHandler handler = new RecordingAlarmHandler();
        final Server server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
            .addService(handler)
            .build()
            .start();
        final int port = server.getPort();
        try {
            Rules initial = new Rules();
            GRPCAlarmSetting setting = setting("restore-hook", true, port);
            initial.getGrpcAlarmSettingMap().put(setting.getFormattedName(), setting);

            AlarmRulesWatcher watcher = new AlarmRulesWatcher(initial, null, null);
            GRPCCallback callback = new GRPCCallback(watcher, noopMetrics(), AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS);

            watcher.notify(new Rules());
            assertDoesNotThrow(() -> callback.doAlarm(Collections.emptyList()));
            assertThat(ReflectUtil.<Map<?, ?>>getInternalState(callback, "alarmServiceStubMap")).isNotNull().isEmpty();
            assertThat(ReflectUtil.<Map<?, ?>>getInternalState(callback, "grpcClientMap")).isNotNull().isEmpty();

            Rules restored = new Rules();
            GRPCAlarmSetting restoredSetting = setting("restore-hook", true, port);
            restored.getGrpcAlarmSettingMap().put(restoredSetting.getFormattedName(), restoredSetting);
            watcher.notify(restored);

            AlarmMessage alarm = alarmMessage(setting.getFormattedName(), "restored-alarm");
            AlarmRecoveryMessage recovery = new AlarmRecoveryMessage(alarm);
            assertDoesNotThrow(() -> callback.doAlarm(Collections.singletonList(alarm)));
            assertDoesNotThrow(() -> callback.doAlarmRecovery(Collections.singletonList(recovery)));

            assertThat(ReflectUtil.<Map<?, ?>>getInternalState(callback, "alarmServiceStubMap")).isNotEmpty();
            assertThat(ReflectUtil.<Map<?, ?>>getInternalState(callback, "grpcClientMap")).isNotEmpty();
            assertThat(handler.alarms).extracting(org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage::getName)
                .containsExactly("restored-alarm");
            assertThat(handler.recoveries)
                .extracting(org.apache.skywalking.oap.server.core.alarm.grpc.AlarmRecoveryMessage::getName)
                .containsExactly("restored-alarm");

            watcher.notify(new Rules());
            callback.doAlarm(Collections.emptyList());
        } finally {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * A non-empty update that drops a hook must shut down that hook's client/stub, while
     * an unchanged host/port keeps the existing client instance.
     */
    @Test
    public void shouldShutdownClientWhenHookRemovedFromNonEmptyConfig() throws IOException {
        final int port = freePort();
        Rules initial = new Rules();
        GRPCAlarmSetting keep = setting("keep-hook", true, port);
        GRPCAlarmSetting drop = setting("drop-hook", false, port);
        initial.getGrpcAlarmSettingMap().put(keep.getFormattedName(), keep);
        initial.getGrpcAlarmSettingMap().put(drop.getFormattedName(), drop);

        AlarmRulesWatcher watcher = new AlarmRulesWatcher(initial, null, null);
        GRPCCallback callback = new GRPCCallback(watcher, noopMetrics(), AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS);
        final GRPCClient keptClient = ReflectUtil.<Map<String, GRPCClient>>getInternalState(
            callback, "grpcClientMap").get(keep.getFormattedName());
        final GRPCClient droppedClient = ReflectUtil.<Map<String, GRPCClient>>getInternalState(
            callback, "grpcClientMap").get(drop.getFormattedName());
        assertThat(keptClient).isNotNull();
        assertThat(droppedClient).isNotNull();

        Rules shrunk = new Rules();
        GRPCAlarmSetting keepOnly = setting("keep-hook", true, port);
        shrunk.getGrpcAlarmSettingMap().put(keepOnly.getFormattedName(), keepOnly);
        watcher.notify(shrunk);
        callback.doAlarm(Collections.emptyList());

        Map<String, GRPCClient> clients = ReflectUtil.getInternalState(callback, "grpcClientMap");
        assertThat(clients.keySet()).containsExactly(keep.getFormattedName());
        assertThat(clients.get(keep.getFormattedName())).isSameAs(keptClient);
        assertThat(ReflectUtil.<Map<String, ?>>getInternalState(callback, "alarmServiceStubMap").keySet())
            .containsExactly(keep.getFormattedName());
        assertThat(droppedClient.getChannel().isShutdown()).isTrue();

        watcher.notify(new Rules());
        callback.doAlarm(Collections.emptyList());
    }

    /**
     * Re-parsing the same host/port into a new settings object must not rebuild the client.
     */
    @Test
    public void shouldKeepClientWhenHostPortUnchangedAcrossReparse() throws IOException {
        final int port = freePort();
        Rules initial = new Rules();
        GRPCAlarmSetting setting = setting("stable-hook", true, port);
        initial.getGrpcAlarmSettingMap().put(setting.getFormattedName(), setting);

        AlarmRulesWatcher watcher = new AlarmRulesWatcher(initial, null, null);
        GRPCCallback callback = new GRPCCallback(watcher, noopMetrics(), AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS);
        final GRPCClient original = ReflectUtil.<Map<String, GRPCClient>>getInternalState(
            callback, "grpcClientMap").get(setting.getFormattedName());

        Rules reparsed = new Rules();
        GRPCAlarmSetting sameTarget = setting("stable-hook", false, port);
        reparsed.getGrpcAlarmSettingMap().put(sameTarget.getFormattedName(), sameTarget);
        watcher.notify(reparsed);
        callback.doAlarm(Collections.emptyList());

        assertThat(ReflectUtil.<Map<String, GRPCClient>>getInternalState(callback, "grpcClientMap")
            .get(setting.getFormattedName())).isSameAs(original);

        watcher.notify(new Rules());
        callback.doAlarm(Collections.emptyList());
    }

    /**
     * A null value in the dynamic settings map must not NPE the rebuild path.
     */
    @Test
    public void shouldIgnoreNullSettingEntryWithoutThrowing() throws IOException {
        final int port = freePort();
        Rules initial = new Rules();
        GRPCAlarmSetting keep = setting("keep-hook", true, port);
        initial.getGrpcAlarmSettingMap().put(keep.getFormattedName(), keep);

        AlarmRulesWatcher watcher = new AlarmRulesWatcher(initial, null, null);
        GRPCCallback callback = new GRPCCallback(watcher, noopMetrics(), AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS);

        Rules dirty = new Rules();
        dirty.getGrpcAlarmSettingMap().put(keep.getFormattedName(), keep);
        dirty.getGrpcAlarmSettingMap().put("null-hook.grpc", null);
        watcher.notify(dirty);

        assertDoesNotThrow(() -> callback.doAlarm(Collections.emptyList()));
        assertThat(ReflectUtil.<Map<String, ?>>getInternalState(callback, "grpcClientMap").keySet())
            .containsExactly(keep.getFormattedName());

        watcher.notify(new Rules());
        callback.doAlarm(Collections.emptyList());
    }

    /**
     * Alarm messages that name a hook missing from settings must be skipped, not NPE
     * inside send when looking up host/port.
     */
    @Test
    public void shouldSkipMessageHookMissingFromSettings() throws IOException {
        final int port = freePort();
        Rules rules = new Rules();
        GRPCAlarmSetting keep = setting("keep-hook", true, port);
        rules.getGrpcAlarmSettingMap().put(keep.getFormattedName(), keep);

        final RecordingDeliveryMetrics metrics = new RecordingDeliveryMetrics();
        AlarmRulesWatcher watcher = new AlarmRulesWatcher(rules, null, null);
        GRPCCallback callback = new GRPCCallback(watcher, metrics.metrics, AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS);

        AlarmMessage message = new AlarmMessage();
        message.setId0("1");
        message.setId1("2");
        message.setScope(Scope.Service.name());
        message.setName("orphan hook message");
        message.setAlarmMessage("message");
        message.setRuleName("mock_rule");
        message.setStartTime(System.currentTimeMillis());
        message.setTags(Arrays.asList(new Tag("key", "value")));
        message.getHooks().add("missing-hook.grpc");

        assertDoesNotThrow(() -> callback.doAlarm(Collections.singletonList(message)));
        assertEquals(0, metrics.totalDeliveries.get());
        assertEquals(0, metrics.latencyObservations.get());

        watcher.notify(new Rules());
        callback.doAlarm(Collections.emptyList());
    }

    /**
     * A malformed target such as "http://host" fails while building the channel. It must
     * disable only its own hook, at boot as well, while the other gRPC hooks still deliver.
     */
    @Test
    public void shouldSkipOnlyTheHookWithMalformedTarget() throws Exception {
        final RecordingAlarmHandler handler = new RecordingAlarmHandler();
        final Server server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
            .addService(handler)
            .build()
            .start();
        try {
            final int port = server.getPort();
            final GRPCAlarmSetting good = setting("good-hook", true, port);
            final GRPCAlarmSetting bad = setting("bad-hook", false, port);
            bad.setTargetHost("http://127.0.0.1");
            // The malformed hook comes first, so the good one is reached only if reconciling continues past it.
            final Map<String, GRPCAlarmSetting> settings = new LinkedHashMap<>();
            settings.put(bad.getFormattedName(), bad);
            settings.put(good.getFormattedName(), good);
            final Rules rules = new Rules();
            rules.setGrpcAlarmSettingMap(settings);

            final AlarmRulesWatcher watcher = new AlarmRulesWatcher(rules, null, null);
            final GRPCCallback callback = assertDoesNotThrow(() -> new GRPCCallback(watcher, noopMetrics(), AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS));
            try {
                assertEquals(Collections.singleton(good.getFormattedName()),
                    ReflectUtil.<Map<String, ?>>getInternalState(callback, "grpcClientMap").keySet());

                final AlarmMessage toGood = alarmMessage(good.getFormattedName(), "good-alarm");
                final AlarmMessage toBad = alarmMessage(bad.getFormattedName(), "bad-alarm");
                assertDoesNotThrow(() -> callback.doAlarm(Arrays.asList(toGood, toBad)));
                assertEquals(Collections.singletonList("good-alarm"), handler.alarms.stream()
                    .map(org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage::getName)
                    .collect(Collectors.toList()));
            } finally {
                watcher.notify(new Rules());
                callback.doAlarm(Collections.emptyList());
            }
        } finally {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /**
     * When an existing hook's target becomes malformed, the hook keeps its previous client
     * rather than failing the callback.
     */
    @Test
    public void shouldKeepPreviousClientWhenTargetBecomesMalformed() throws IOException {
        final int port = freePort();
        final Rules initial = new Rules();
        final GRPCAlarmSetting setting = setting("moving-hook", true, port);
        initial.getGrpcAlarmSettingMap().put(setting.getFormattedName(), setting);

        final AlarmRulesWatcher watcher = new AlarmRulesWatcher(initial, null, null);
        final GRPCCallback callback = new GRPCCallback(watcher, noopMetrics(), AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS);
        try {
            final GRPCClient original = ReflectUtil.<Map<String, GRPCClient>>getInternalState(
                callback, "grpcClientMap").get(setting.getFormattedName());

            final Rules malformed = new Rules();
            final GRPCAlarmSetting badTarget = setting("moving-hook", true, port);
            badTarget.setTargetHost("127.0.0.1:" + port);
            malformed.getGrpcAlarmSettingMap().put(badTarget.getFormattedName(), badTarget);
            watcher.notify(malformed);
            assertDoesNotThrow(() -> callback.doAlarm(Collections.emptyList()));

            assertSame(original, ReflectUtil.<Map<String, GRPCClient>>getInternalState(callback, "grpcClientMap")
                .get(setting.getFormattedName()));
            assertFalse(original.getChannel().isShutdown());
        } finally {
            watcher.notify(new Rules());
            callback.doAlarm(Collections.emptyList());
        }
    }

    /**
     * Changing the host replaces the client, shuts down the previous channel, and leaves the
     * replacement channel open.
     */
    @Test
    public void shouldReplaceClientWhenTargetChanges() throws IOException {
        final int port = freePort();
        final Rules initial = new Rules();
        final GRPCAlarmSetting setting = setting("moving-hook", true, port);
        initial.getGrpcAlarmSettingMap().put(setting.getFormattedName(), setting);

        final AlarmRulesWatcher watcher = new AlarmRulesWatcher(initial, null, null);
        final GRPCCallback callback = new GRPCCallback(watcher, noopMetrics(), AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS);
        final GRPCClient original = ReflectUtil.<Map<String, GRPCClient>>getInternalState(
            callback, "grpcClientMap").get(setting.getFormattedName());
        try {
            final Rules moved = new Rules();
            final GRPCAlarmSetting newTarget = setting("moving-hook", true, port);
            newTarget.setTargetHost("localhost");
            moved.getGrpcAlarmSettingMap().put(newTarget.getFormattedName(), newTarget);
            watcher.notify(moved);
            callback.doAlarm(Collections.emptyList());

            final GRPCClient replacement = ReflectUtil.<Map<String, GRPCClient>>getInternalState(
                callback, "grpcClientMap").get(setting.getFormattedName());
            assertNotSame(original, replacement);
            assertEquals("localhost", replacement.getHost());
            assertTrue(original.getChannel().isShutdown());
            assertFalse(replacement.getChannel().isShutdown());
        } finally {
            watcher.notify(new Rules());
            callback.doAlarm(Collections.emptyList());
            // A failed replacement may have dropped the original from the map without shutting it down.
            original.shutdown();
        }
    }

    @Test
    public void shouldRecordSuccessMetricsAgainstLiveServer() throws Exception {
        final RecordingAlarmHandler handler = new RecordingAlarmHandler();
        final Server server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
            .addService(handler)
            .build()
            .start();
        final int port = server.getPort();
        final RecordingDeliveryMetrics metrics = new RecordingDeliveryMetrics();
        try {
            Rules rules = new Rules();
            GRPCAlarmSetting setting = setting("metrics-hook", true, port);
            rules.getGrpcAlarmSettingMap().put(setting.getFormattedName(), setting);
            AlarmRulesWatcher watcher = new AlarmRulesWatcher(rules, null, null);
            GRPCCallback callback = new GRPCCallback(watcher, metrics.metrics, AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS);
            try {
                AlarmMessage alarm = alarmMessage(setting.getFormattedName(), "metrics-alarm");
                callback.doAlarm(Collections.singletonList(alarm));
                callback.doAlarmRecovery(Collections.singletonList(new AlarmRecoveryMessage(alarm)));

                assertEquals(2, metrics.successDeliveries.get());
                assertEquals(2, metrics.latencyObservations.get());
                assertTrue(metrics.lastLatencySeconds.get() >= 0);
            } finally {
                watcher.notify(new Rules());
                callback.doAlarm(Collections.emptyList());
            }
        } finally {
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    @Test
    public void shouldRecordUnavailableWhenTargetRefuses() throws Exception {
        final int closedPort = freePort();
        final RecordingDeliveryMetrics metrics = new RecordingDeliveryMetrics();
        Rules rules = new Rules();
        GRPCAlarmSetting setting = setting("down-hook", true, closedPort);
        rules.getGrpcAlarmSettingMap().put(setting.getFormattedName(), setting);
        AlarmRulesWatcher watcher = new AlarmRulesWatcher(rules, null, null);
        GRPCCallback callback = new GRPCCallback(watcher, metrics.metrics, AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS);
        try {
            AlarmMessage alarm = alarmMessage(setting.getFormattedName(), "down-alarm");
            callback.doAlarm(Collections.singletonList(alarm));

            assertEquals(1, metrics.ioErrorDeliveries.get());
            assertEquals(0, metrics.timeoutDeliveries.get());
            assertEquals(1, metrics.latencyObservations.get());
        } finally {
            watcher.notify(new Rules());
            callback.doAlarm(Collections.emptyList());
        }
    }

    /** Foreign hooks (webhook/slack) must not inflate gRPC delivery so11y. */
    @Test
    public void shouldIgnoreForeignHooksWithoutRecordingMetrics() {
        final RecordingDeliveryMetrics metrics = new RecordingDeliveryMetrics();
        AlarmRulesWatcher watcher = new AlarmRulesWatcher(new Rules(), null, null);
        GRPCCallback callback = new GRPCCallback(watcher, metrics.metrics, AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS);

        AlarmMessage message = new AlarmMessage();
        message.setId0("1");
        message.setScope(Scope.Service.name());
        message.setName("mixed hooks");
        message.setAlarmMessage("message");
        message.setRuleName("mock_rule");
        message.setStartTime(System.currentTimeMillis());
        message.getHooks().add(AlarmHooksType.webhook.name() + ".custom");
        message.getHooks().add(AlarmHooksType.gRPC.name() + ".gone");

        assertDoesNotThrow(() -> callback.doAlarm(Collections.singletonList(message)));
        assertEquals(0, metrics.totalDeliveries.get());
    }

    /** Cleared gRPC settings must not record delivery metrics. */
    @Test
    public void shouldNotRecordWhenAllGrpcSettingsCleared() throws IOException {
        final int port = freePort();
        Rules initial = new Rules();
        GRPCAlarmSetting setting = setting("cleared-hook", true, port);
        initial.getGrpcAlarmSettingMap().put(setting.getFormattedName(), setting);

        final RecordingDeliveryMetrics metrics = new RecordingDeliveryMetrics();
        AlarmRulesWatcher watcher = new AlarmRulesWatcher(initial, null, null);
        GRPCCallback callback = new GRPCCallback(watcher, metrics.metrics, AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS);

        watcher.notify(new Rules());
        assertDoesNotThrow(() -> callback.doAlarm(
            Collections.singletonList(alarmMessage(setting.getFormattedName(), "cleared-settings-alarm"))
        ));
        assertEquals(0, metrics.totalDeliveries.get());
        assertEquals(0, metrics.latencyObservations.get());
    }

    /** Interrupt during wait → {@code interrupted}, without restoring the interrupt flag. */
    @Test
    public void shouldRecordInterruptedWhenCallerInterruptedDuringWait() throws Exception {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch entered = new CountDownLatch(1);
        final Server server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
            .addService(new StallingAlarmHandler(entered, release))
            .build()
            .start();
        final int port = server.getPort();
        final RecordingDeliveryMetrics metrics = new RecordingDeliveryMetrics();
        try {
            Rules rules = new Rules();
            GRPCAlarmSetting setting = setting("interrupt-hook", true, port);
            rules.getGrpcAlarmSettingMap().put(setting.getFormattedName(), setting);
            AlarmRulesWatcher watcher = new AlarmRulesWatcher(rules, null, null);
            GRPCCallback callback = new GRPCCallback(watcher, metrics.metrics, AlarmModuleConfig.DEFAULT_GRPC_TIMEOUT_SECONDS);

            final Thread caller = new Thread(() ->
                callback.doAlarm(Collections.singletonList(alarmMessage(setting.getFormattedName(), "interrupt-alarm")))
            );
            caller.start();
            try {
                assertTrue(entered.await(5, TimeUnit.SECONDS));
                caller.interrupt();
                caller.join(10_000);
                assertFalse(caller.isAlive(), "caller should finish after interrupt without waiting for the peer");

                assertEquals(1, metrics.interruptedDeliveries.get());
                assertEquals(1, metrics.latencyObservations.get());
            } finally {
                // The callback is single-threaded: stop the caller before the cleanup call touches its clients.
                caller.interrupt();
                caller.join(10_000);
                watcher.notify(new Rules());
                callback.doAlarm(Collections.emptyList());
            }
        } finally {
            release.countDown();
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    /** Peer past deadline → {@code timeout} ({@code DEADLINE_EXCEEDED}). */
    @Test
    public void shouldRecordTimeoutWhenPeerExceedsDeadline() throws Exception {
        final CountDownLatch release = new CountDownLatch(1);
        final CountDownLatch entered = new CountDownLatch(1);
        final Server server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", 0))
            .addService(new StallingAlarmHandler(entered, release))
            .build()
            .start();
        final int port = server.getPort();
        final RecordingDeliveryMetrics metrics = new RecordingDeliveryMetrics();
        try {
            Rules rules = new Rules();
            GRPCAlarmSetting setting = setting("timeout-hook", true, port);
            rules.getGrpcAlarmSettingMap().put(setting.getFormattedName(), setting);
            AlarmRulesWatcher watcher = new AlarmRulesWatcher(rules, null, null);
            GRPCCallback callback = new GRPCCallback(watcher, metrics.metrics, 1L);
            try {
                callback.doAlarm(Collections.singletonList(alarmMessage(setting.getFormattedName(), "timeout-alarm")));

                assertTrue(entered.await(5, TimeUnit.SECONDS));
                assertEquals(1, metrics.timeoutDeliveries.get());
                assertEquals(1, metrics.latencyObservations.get());
            } finally {
                watcher.notify(new Rules());
                callback.doAlarm(Collections.emptyList());
            }
        } finally {
            release.countDown();
            server.shutdownNow();
            server.awaitTermination(5, TimeUnit.SECONDS);
        }
    }

    private static GRPCAlarmSetting setting(String name, boolean isDefault, int port) {
        GRPCAlarmSetting setting = new GRPCAlarmSetting(name, AlarmHooksType.gRPC, isDefault);
        setting.setTargetHost("127.0.0.1");
        setting.setTargetPort(port);
        return setting;
    }

    private static AlarmMessage alarmMessage(String hook, String name) {
        AlarmMessage message = new AlarmMessage();
        message.setId0("1");
        message.setId1("2");
        message.setScope(Scope.Service.name());
        message.setName(name);
        message.setAlarmMessage("message");
        message.setRuleName("mock_rule");
        message.setStartTime(System.currentTimeMillis());
        message.setTags(Arrays.asList(new Tag("key", "value")));
        message.getHooks().add(hook);
        return message;
    }

    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static AlarmHttpDeliveryMetrics noopMetrics() {
        return new AlarmHttpDeliveryMetrics(new MetricsCreatorNoop());
    }

    private static final class RecordingDeliveryMetrics {
        private final AtomicInteger totalDeliveries = new AtomicInteger();
        private final AtomicInteger successDeliveries = new AtomicInteger();
        private final AtomicInteger ioErrorDeliveries = new AtomicInteger();
        private final AtomicInteger interruptedDeliveries = new AtomicInteger();
        private final AtomicInteger timeoutDeliveries = new AtomicInteger();
        private final AtomicInteger latencyObservations = new AtomicInteger();
        private final AtomicReference<Double> lastLatencySeconds = new AtomicReference<>(0.0);
        private final AlarmHttpDeliveryMetrics metrics;

        private RecordingDeliveryMetrics() {
            metrics = new AlarmHttpDeliveryMetrics(new MetricsCreatorNoop() {
                @Override
                public CounterMetrics createCounter(final String name, final String tips, final MetricsTag.Keys tagKeys,
                                                    final MetricsTag.Values tagValues) {
                    if (!"alarm_http_delivery_total".equals(name)) {
                        return super.createCounter(name, tips, tagKeys, tagValues);
                    }
                    final String hookType = tagValues.getValues()[0];
                    final String result = tagValues.getValues()[1];
                    return new CounterMetrics() {
                        @Override
                        public void inc() {
                            if (!AlarmHooksType.gRPC.name().equals(hookType)) {
                                return;
                            }
                            totalDeliveries.incrementAndGet();
                            if (AlarmHttpDeliveryMetrics.RESULT_SUCCESS.equals(result)) {
                                successDeliveries.incrementAndGet();
                            } else if (AlarmHttpDeliveryMetrics.RESULT_IO_ERROR.equals(result)) {
                                ioErrorDeliveries.incrementAndGet();
                            } else if (AlarmHttpDeliveryMetrics.RESULT_INTERRUPTED.equals(result)) {
                                interruptedDeliveries.incrementAndGet();
                            } else if (AlarmHttpDeliveryMetrics.RESULT_TIMEOUT.equals(result)) {
                                timeoutDeliveries.incrementAndGet();
                            }
                        }

                        @Override
                        public void inc(final double value) {
                            inc();
                        }
                    };
                }

                @Override
                public HistogramMetrics createHistogramMetric(final String name, final String tips,
                                                             final MetricsTag.Keys tagKeys,
                                                             final MetricsTag.Values tagValues,
                                                             final double... buckets) {
                    if (!"alarm_http_delivery_latency_seconds".equals(name)) {
                        return super.createHistogramMetric(name, tips, tagKeys, tagValues, buckets);
                    }
                    return new HistogramMetrics() {
                        @Override
                        public void observe(final double value) {
                            latencyObservations.incrementAndGet();
                            lastLatencySeconds.set(value);
                        }
                    };
                }
            });
        }
    }

    private static final class StallingAlarmHandler extends AlarmServiceGrpc.AlarmServiceImplBase {
        private final CountDownLatch entered;
        private final CountDownLatch release;

        private StallingAlarmHandler(final CountDownLatch entered, final CountDownLatch release) {
            this.entered = entered;
            this.release = release;
        }

        @Override
        public StreamObserver<org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage> doAlarm(
            StreamObserver<Response> responseObserver) {
            return new StreamObserver<org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage>() {
                @Override
                public void onNext(org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage value) {
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    entered.countDown();
                    try {
                        release.await(30, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    responseObserver.onNext(Response.newBuilder().build());
                    responseObserver.onCompleted();
                }
            };
        }
    }

    private static final class RecordingAlarmHandler extends AlarmServiceGrpc.AlarmServiceImplBase {
        private final List<org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage> alarms =
            new CopyOnWriteArrayList<>();
        private final List<org.apache.skywalking.oap.server.core.alarm.grpc.AlarmRecoveryMessage> recoveries =
            new CopyOnWriteArrayList<>();

        @Override
        public StreamObserver<org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage> doAlarm(
            StreamObserver<Response> responseObserver) {
            return new StreamObserver<org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage>() {
                @Override
                public void onNext(org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage value) {
                    alarms.add(value);
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onNext(Response.newBuilder().build());
                    responseObserver.onCompleted();
                }
            };
        }

        @Override
        public StreamObserver<org.apache.skywalking.oap.server.core.alarm.grpc.AlarmRecoveryMessage> doAlarmRecovery(
            StreamObserver<Response> responseObserver) {
            return new StreamObserver<org.apache.skywalking.oap.server.core.alarm.grpc.AlarmRecoveryMessage>() {
                @Override
                public void onNext(org.apache.skywalking.oap.server.core.alarm.grpc.AlarmRecoveryMessage value) {
                    recoveries.add(value);
                }

                @Override
                public void onError(Throwable throwable) {
                    responseObserver.onError(throwable);
                }

                @Override
                public void onCompleted() {
                    responseObserver.onNext(Response.newBuilder().build());
                    responseObserver.onCompleted();
                }
            };
        }
    }

    private void mockAlarmMessage(String hook1, String hook2) {
        AlarmMessage alarmMessage = alarmMessage(hook1, "mock alarm message");
        AlarmMessage alarmMessage2 = alarmMessage(hook1, "mock alarm message2");
        alarmMessage2.setId0("21");
        alarmMessage2.setId1("22");
        alarmMessage2.setRuleName("mock_rule2");
        alarmMessage2.setTags(Arrays.asList(new Tag("key2", "value2")));
        alarmMessageList = Lists.newArrayList(alarmMessage, alarmMessage2);
    }

    private void mockAlarmRecoveryMessage(String hook1, String hook2) {
        AlarmRecoveryMessage alarmRecoveryMessage0 = new AlarmRecoveryMessage(alarmMessageList.get(0));
        AlarmRecoveryMessage alarmRecoveryMessage1 = new AlarmRecoveryMessage(alarmMessageList.get(1));

        alarmRecoveryMessageList = Lists.newArrayList(alarmRecoveryMessage0, alarmRecoveryMessage1);
    }
}
