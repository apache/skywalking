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

import io.grpc.stub.ClientCallStreamObserver;
import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmHttpDeliveryMetrics;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecoveryMessage;
import org.apache.skywalking.oap.server.core.alarm.grpc.AlarmServiceGrpc;
import org.apache.skywalking.oap.server.core.alarm.grpc.AlarmTags;
import org.apache.skywalking.oap.server.core.alarm.grpc.KeyStringValuePair;
import org.apache.skywalking.oap.server.core.alarm.grpc.Response;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmHooksType;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;
import org.apache.skywalking.oap.server.library.util.GRPCStreamStatus;

/**
 * Use SkyWalking alarm grpc API call a remote methods.
 */
@Slf4j
public class GRPCCallback implements AlarmCallback {

    private final AlarmRulesWatcher alarmRulesWatcher;

    private final AlarmHttpDeliveryMetrics deliveryMetrics;

    private final long deadlineSeconds;

    private final Map<String, AlarmServiceGrpc.AlarmServiceStub> alarmServiceStubMap;

    private final Map<String, GRPCClient> grpcClientMap;

    public GRPCCallback(final AlarmRulesWatcher alarmRulesWatcher,
                        final AlarmHttpDeliveryMetrics deliveryMetrics,
                        final long deadlineSeconds) {
        this.alarmRulesWatcher = alarmRulesWatcher;
        this.deliveryMetrics = Objects.requireNonNull(deliveryMetrics, "deliveryMetrics");
        this.deadlineSeconds = deadlineSeconds;
        this.alarmServiceStubMap = new HashMap<>();
        this.grpcClientMap = new HashMap<>();
        onGRPCAlarmSettingUpdated(alarmRulesWatcher.getGrpchookSetting());
    }

    @Override
    public void doAlarm(List<AlarmMessage> alarmMessages) {
        doAlarmCallback(alarmMessages, false);
    }

    @Override
    public void doAlarmRecovery(List<AlarmMessage> alarmRecoveryMessages) {
        doAlarmCallback(alarmRecoveryMessages, true);
    }

    private void doAlarmCallback(List<AlarmMessage> alarmMessages, boolean isRecovery) {
        // recreate gRPC client and stub if host and port configuration changed.
        Map<String, GRPCAlarmSetting> settinsMap = alarmRulesWatcher.getGrpchookSetting();
        onGRPCAlarmSettingUpdated(settinsMap);

        if (settinsMap == null || settinsMap.isEmpty()) {
            return;
        }

        groupMessagesByHook(alarmMessages).forEach((hook, messages) -> {
            final GRPCAlarmSetting setting = settinsMap.get(hook);
            if (setting == null) {
                return;
            }
            final AlarmServiceGrpc.AlarmServiceStub stub = alarmServiceStubMap.get(hook);
            if (stub == null) {
                return;
            }
            sendMessages(stub, messages, setting, isRecovery);
        });
    }

    /** Open the alarm or recovery stream, wait (or cancel on interrupt), then record so11y. */
    private void sendMessages(final AlarmServiceGrpc.AlarmServiceStub alarmServiceStub,
                              final List<AlarmMessage> alarmMessages,
                              final GRPCAlarmSetting alarmSetting,
                              final boolean recovery) {
        final long startedNanos = System.nanoTime();
        final GRPCStreamStatus status = new GRPCStreamStatus();
        final AtomicReference<Throwable> streamError = new AtomicReference<>();
        boolean interrupted = false;
        final String label = recovery ? "alarm recovery" : "alarm";
        try {
            final AlarmServiceGrpc.AlarmServiceStub timed =
                alarmServiceStub.withDeadlineAfter(deadlineSeconds, TimeUnit.SECONDS);
            final StreamObserver<?> requestObserver;
            if (recovery) {
                final StreamObserver<org.apache.skywalking.oap.server.core.alarm.grpc.AlarmRecoveryMessage> out =
                    timed.doAlarmRecovery(responseObserver(streamError, status, label));
                for (AlarmMessage message : alarmMessages) {
                    out.onNext(toGrpcRecoveryMessage((AlarmRecoveryMessage) message));
                }
                out.onCompleted();
                requestObserver = out;
            } else {
                final StreamObserver<org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage> out =
                    timed.doAlarm(responseObserver(streamError, status, label));
                for (AlarmMessage message : alarmMessages) {
                    out.onNext(toGrpcAlarmMessage(message));
                }
                out.onCompleted();
                requestObserver = out;
            }
            interrupted = waitUntilDone(
                status, alarmMessages.size(), alarmSetting, label, cancelOnInterrupt(requestObserver, status));
        } catch (Throwable throwable) {
            streamError.compareAndSet(null, throwable);
            status.done();
            log.warn("Send {} message failed before the peer replied: {}", label, throwable.getMessage());
        } finally {
            recordDelivery(startedNanos, interrupted, streamError.get());
        }
    }

    private static StreamObserver<Response> responseObserver(final AtomicReference<Throwable> streamError,
                                                             final GRPCStreamStatus status,
                                                             final String label) {
        return new StreamObserver<Response>() {
            @Override
            public void onNext(Response response) {
                // ignore empty response
            }

            @Override
            public void onError(Throwable throwable) {
                streamError.set(throwable);
                status.done();
                log.warn("Send {} message failed: {}", label, throwable.getMessage());
            }

            @Override
            public void onCompleted() {
                status.done();
                if (log.isDebugEnabled()) {
                    log.debug("Send {} message successful.", label);
                }
            }
        };
    }

    private static org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage toGrpcAlarmMessage(
        final AlarmMessage message) {
        return org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage.newBuilder()
            .setScopeId(message.getScopeId())
            .setScope(message.getScope())
            .setName(message.getName())
            .setId0(message.getId0())
            .setId1(message.getId1())
            .setRuleName(message.getRuleName())
            .setAlarmMessage(message.getAlarmMessage())
            .setStartTime(message.getStartTime())
            .setUuid(message.getUuid())
            .setTags(toAlarmTags(message))
            .build();
    }

    private static org.apache.skywalking.oap.server.core.alarm.grpc.AlarmRecoveryMessage toGrpcRecoveryMessage(
        final AlarmRecoveryMessage message) {
        return org.apache.skywalking.oap.server.core.alarm.grpc.AlarmRecoveryMessage.newBuilder()
            .setScopeId(message.getScopeId())
            .setScope(message.getScope())
            .setName(message.getName())
            .setId0(message.getId0())
            .setId1(message.getId1())
            .setRuleName(message.getRuleName())
            .setAlarmMessage(message.getAlarmMessage())
            .setStartTime(message.getStartTime())
            .setUuid(message.getUuid())
            .setRecoveryTime(message.getRecoveryTime())
            .setTags(toAlarmTags(message))
            .build();
    }

    private static AlarmTags toAlarmTags(final AlarmMessage message) {
        final AlarmTags.Builder alarmTagsBuilder = AlarmTags.newBuilder();
        message.getTags().forEach(m ->
            alarmTagsBuilder.addData(KeyStringValuePair.newBuilder().setKey(m.getKey()).setValue(m.getValue()).build())
        );
        return alarmTagsBuilder.build();
    }

    private boolean waitUntilDone(final GRPCStreamStatus status,
                                  final int messageCount,
                                  final GRPCAlarmSetting alarmSetting,
                                  final String label,
                                  final Runnable cancelOnInterrupt) {
        long sleepTime = 0;
        long cycle = 100L;
        boolean interrupted = false;

        // For memory safe of oap, we must wait for the peer confirmation unless interrupted.
        while (!status.isDone()) {
            try {
                sleepTime += cycle;
                Thread.sleep(cycle);
            } catch (InterruptedException e) {
                // Leave interrupt cleared so later hooks on this AlarmCore thread still run.
                interrupted = true;
                if (cancelOnInterrupt != null) {
                    try {
                        cancelOnInterrupt.run();
                    } catch (Throwable ignored) {
                        // ignore cancel failures
                    }
                }
                break;
            }

            if (log.isDebugEnabled()) {
                log.debug("Send {} {} message to {}:{}.", messageCount, label,
                    alarmSetting.getTargetHost(), alarmSetting.getTargetPort()
                );
            }

            if (sleepTime > 2000L) {
                log.warn("Send {} {} message to {}:{}, wait {} milliseconds.", messageCount, label,
                    alarmSetting.getTargetHost(), alarmSetting.getTargetPort(), sleepTime
                );
                cycle = 2000L;
            }
        }
        return interrupted;
    }

    private static Runnable cancelOnInterrupt(final StreamObserver<?> streamObserver,
                                              final GRPCStreamStatus status) {
        return () -> {
            if (streamObserver instanceof ClientCallStreamObserver) {
                ((ClientCallStreamObserver<?>) streamObserver).cancel("alarm delivery interrupted", null);
            }
            status.done();
        };
    }

    private void recordDelivery(final long startedNanos,
                                final boolean interrupted,
                                final Throwable streamError) {
        deliveryMetrics.record(
            AlarmHooksType.gRPC.name(),
            AlarmHttpDeliveryMetrics.classifyGrpcDeliveryResult(interrupted, streamError),
            null,
            (System.nanoTime() - startedNanos) / 1.0E9D
        );
    }

    private void onGRPCAlarmSettingUpdated(Map<String, GRPCAlarmSetting> newAlarmSettingMap) {
        if (newAlarmSettingMap == null || newAlarmSettingMap.isEmpty()) {
            // Keep maps non-null so a later non-empty dynamic config update can rebuild
            // clients without NPE on grpcClientMap / alarmServiceStubMap access.
            grpcClientMap.forEach((name, grpcClient) -> {
                grpcClient.shutdown();
                log.debug("gRPC alarm hook target is empty, shutdown the old gRPC client.");
            });
            grpcClientMap.clear();
            alarmServiceStubMap.clear();
            return;
        }

        // Drop clients for hooks that disappeared from the new config (e.g. {A,B} -> {A}).
        for (String name : new ArrayList<>(grpcClientMap.keySet())) {
            if (!newAlarmSettingMap.containsKey(name)) {
                shutdownClient(name, "removed");
            }
        }

        newAlarmSettingMap.forEach((name, newAlarmSetting) -> {
            if (newAlarmSetting == null || newAlarmSetting.isEmptySetting()) {
                shutdownClient(name, "empty");
                return;
            }
            final GRPCClient existing = grpcClientMap.get(name);
            if (sameTarget(existing, newAlarmSetting)) {
                return;
            }
            final GRPCClient grpcClient = new GRPCClient(newAlarmSetting.getTargetHost(), newAlarmSetting.getTargetPort());
            try {
                grpcClient.connect();
            } catch (RuntimeException e) {
                // A malformed target, such as "http://host" or "host:port" as the host, fails here.
                // Skip only this hook so the other gRPC hooks still reconcile and send.
                log.error("Cannot build the gRPC client of alarm hook {} for target {}:{}, {}.",
                          name, newAlarmSetting.getTargetHost(), newAlarmSetting.getTargetPort(),
                          existing == null ? "the hook is skipped"
                              : "it keeps the previous target " + existing.getHost() + ":" + existing.getPort(),
                          e);
                return;
            }
            final GRPCClient previous = grpcClientMap.put(name, grpcClient);
            alarmServiceStubMap.put(name, AlarmServiceGrpc.newStub(grpcClient.getChannel()));
            if (previous != null) {
                previous.shutdown();
                log.debug("gRPC alarm hook target is changed, shutdown the old gRPC client.");
            }
        });
    }

    private static boolean sameTarget(GRPCClient existing, GRPCAlarmSetting setting) {
        return existing != null
            && Objects.equals(existing.getHost(), setting.getTargetHost())
            && existing.getPort() == setting.getTargetPort();
    }

    private void shutdownClient(String name, String reason) {
        GRPCClient grpcClient = grpcClientMap.remove(name);
        alarmServiceStubMap.remove(name);
        if (grpcClient != null) {
            grpcClient.shutdown();
            log.debug("gRPC alarm hook target is {}, shutdown the old gRPC client.", reason);
        }
    }
}
