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
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecoveryMessage;
import org.apache.skywalking.oap.server.core.alarm.grpc.AlarmServiceGrpc;
import org.apache.skywalking.oap.server.core.alarm.grpc.Response;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHooksType;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.alarm.provider.Rules;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Scope;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;
import org.apache.skywalking.oap.server.testing.util.ReflectUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

public class GRPChookCallbackTest {

    private GRPCCallback grpcCallback;

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
        grpcCallback = new GRPCCallback(alarmRulesWatcher);
        mockAlarmMessage(setting1.getFormattedName(), setting2.getFormattedName());
        mockAlarmRecoveryMessage(setting1.getFormattedName(), setting2.getFormattedName());
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
        grpcCallback = new GRPCCallback(alarmRulesWatcher);
        grpcCallback.doAlarm(alarmMessageList);
        grpcCallback.doAlarmRecovery(alarmRecoveryMessageList);
    }

    /**
     * Dynamic config clearing gRPC hooks used to null the stub/client maps; restoring
     * targets then NPE'd on the next alarm. After restore, real alarm and recovery
     * messages must reach a live hook server — not only leave maps non-empty.
     */
    @Test
    public void shouldKeepMapsUsableAfterClearThenRestore() throws Exception {
        final int port = freePort();
        final RecordingAlarmHandler handler = new RecordingAlarmHandler();
        final Server server = NettyServerBuilder.forAddress(new InetSocketAddress("127.0.0.1", port))
            .addService(handler)
            .build()
            .start();
        try {
            Rules initial = new Rules();
            GRPCAlarmSetting setting = setting("restore-hook", true, port);
            initial.getGrpcAlarmSettingMap().put(setting.getFormattedName(), setting);

            AlarmRulesWatcher watcher = new AlarmRulesWatcher(initial, null, null);
            GRPCCallback callback = new GRPCCallback(watcher);

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
        GRPCCallback callback = new GRPCCallback(watcher);
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
        GRPCCallback callback = new GRPCCallback(watcher);
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
        GRPCCallback callback = new GRPCCallback(watcher);

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

        AlarmRulesWatcher watcher = new AlarmRulesWatcher(rules, null, null);
        GRPCCallback callback = new GRPCCallback(watcher);

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

        watcher.notify(new Rules());
        callback.doAlarm(Collections.emptyList());
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
