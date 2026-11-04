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

import io.grpc.stub.StreamObserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecoveryMessage;
import org.apache.skywalking.oap.server.core.alarm.grpc.AlarmServiceGrpc;
import org.apache.skywalking.oap.server.core.alarm.grpc.AlarmTags;
import org.apache.skywalking.oap.server.core.alarm.grpc.KeyStringValuePair;
import org.apache.skywalking.oap.server.core.alarm.grpc.Response;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;
import org.apache.skywalking.oap.server.library.util.GRPCStreamStatus;

/**
 * Use SkyWalking alarm grpc API call a remote methods.
 */
@Slf4j
public class GRPCCallback implements AlarmCallback {

    private final AlarmRulesWatcher alarmRulesWatcher;

    private final Map<String, AlarmServiceGrpc.AlarmServiceStub> alarmServiceStubMap;

    private final Map<String, GRPCClient> grpcClientMap;

    public GRPCCallback(AlarmRulesWatcher alarmRulesWatcher) {
        this.alarmRulesWatcher = alarmRulesWatcher;
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
        Map<String, List<AlarmMessage>> groupedMessages = groupMessagesByHook(alarmMessages);

        groupedMessages.forEach((hook, messages) -> {
            final AlarmServiceGrpc.AlarmServiceStub stub = alarmServiceStubMap.get(hook);
            final GRPCAlarmSetting setting = settinsMap.get(hook);
            if (stub == null || setting == null) {
                return;
            }
            if (!isRecovery) {
                sendAlarmMessages(stub, messages, setting);
            } else {
                sendAlarmRecoveryMessages(stub, messages, setting);
            }
        });

    }

    private void sendAlarmMessages(AlarmServiceGrpc.AlarmServiceStub alarmServiceStub,
                                   List<AlarmMessage> alarmMessages,
                                   GRPCAlarmSetting alarmSetting) {
        GRPCStreamStatus status = new GRPCStreamStatus();

        StreamObserver<org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage> streamObserver =
                alarmServiceStub.withDeadlineAfter(10, TimeUnit.SECONDS).doAlarm(new StreamObserver<Response>() {
                    @Override
                    public void onNext(Response response) {
                        // ignore empty response
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        status.done();
                        log.warn("Send alarm message failed: {}", throwable.getMessage());
                    }

                    @Override
                    public void onCompleted() {
                        status.done();
                        if (log.isDebugEnabled()) {
                            log.debug("Send alarm message successful.");
                        }
                    }
                });

        alarmMessages.forEach(message -> {
            org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage.Builder builder =
                    org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage.newBuilder();

            builder.setScopeId(message.getScopeId());
            builder.setScope(message.getScope());
            builder.setName(message.getName());
            builder.setId0(message.getId0());
            builder.setId1(message.getId1());
            builder.setRuleName(message.getRuleName());
            builder.setAlarmMessage(message.getAlarmMessage());
            builder.setStartTime(message.getStartTime());
            builder.setUuid(message.getUuid());
            AlarmTags.Builder alarmTagsBuilder = AlarmTags.newBuilder();
            message.getTags().forEach(m -> alarmTagsBuilder.addData(KeyStringValuePair.newBuilder().setKey(m.getKey()).setValue(m.getValue()).build()));
            builder.setTags(alarmTagsBuilder.build());
            streamObserver.onNext(builder.build());
        });

        streamObserver.onCompleted();

        long sleepTime = 0;
        long cycle = 100L;

        // For memory safe of oap, we must wait for the peer confirmation.
        while (!status.isDone()) {
            try {
                sleepTime += cycle;
                Thread.sleep(cycle);
            } catch (InterruptedException ignored) {
            }

            if (log.isDebugEnabled()) {
                log.debug("Send {} alarm message to {}:{}.", alarmMessages.size(),
                        alarmSetting.getTargetHost(), alarmSetting.getTargetPort()
                );
            }

            if (sleepTime > 2000L) {
                log.warn("Send {} alarm message to {}:{}, wait {} milliseconds.", alarmMessages.size(),
                        alarmSetting.getTargetHost(), alarmSetting.getTargetPort(), sleepTime
                );
                cycle = 2000L;
            }
        }
    }

    private void sendAlarmRecoveryMessages(AlarmServiceGrpc.AlarmServiceStub alarmServiceStub,
                                           List<AlarmMessage> alarmMessages,
                                           GRPCAlarmSetting alarmSetting) {
        GRPCStreamStatus status = new GRPCStreamStatus();

        StreamObserver<org.apache.skywalking.oap.server.core.alarm.grpc.AlarmRecoveryMessage> streamObserver =
                alarmServiceStub.withDeadlineAfter(10, TimeUnit.SECONDS).doAlarmRecovery(new StreamObserver<Response>() {
                    @Override
                    public void onNext(Response response) {
                        // ignore empty response
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        status.done();
                        log.warn("Send alarm recovery message failed: {}", throwable.getMessage());
                    }

                    @Override
                    public void onCompleted() {
                        status.done();
                        if (log.isDebugEnabled()) {
                            log.debug("Send alarm recovery message successful.");
                        }
                    }
                });

        alarmMessages.forEach(message -> {
            org.apache.skywalking.oap.server.core.alarm.grpc.AlarmRecoveryMessage.Builder builder =
                    org.apache.skywalking.oap.server.core.alarm.grpc.AlarmRecoveryMessage.newBuilder();
            AlarmRecoveryMessage recoveryMessage = (AlarmRecoveryMessage) message;
            builder.setScopeId(recoveryMessage.getScopeId());
            builder.setScope(recoveryMessage.getScope());
            builder.setName(recoveryMessage.getName());
            builder.setId0(recoveryMessage.getId0());
            builder.setId1(recoveryMessage.getId1());
            builder.setRuleName(recoveryMessage.getRuleName());
            builder.setAlarmMessage(recoveryMessage.getAlarmMessage());
            builder.setStartTime(recoveryMessage.getStartTime());
            builder.setUuid(recoveryMessage.getUuid());
            builder.setRecoveryTime(recoveryMessage.getRecoveryTime());
            AlarmTags.Builder alarmTagsBuilder = AlarmTags.newBuilder();
            message.getTags().forEach(m -> alarmTagsBuilder.addData(KeyStringValuePair.newBuilder().setKey(m.getKey()).setValue(m.getValue()).build()));
            builder.setTags(alarmTagsBuilder.build());
            streamObserver.onNext(builder.build());
        });

        streamObserver.onCompleted();

        long sleepTime = 0;
        long cycle = 100L;

        // For memory safe of oap, we must wait for the peer confirmation.
        while (!status.isDone()) {
            try {
                sleepTime += cycle;
                Thread.sleep(cycle);
            } catch (InterruptedException ignored) {
            }

            if (log.isDebugEnabled()) {
                log.debug("Send {} alarm recovery message to {}:{}.", alarmMessages.size(),
                        alarmSetting.getTargetHost(), alarmSetting.getTargetPort()
                );
            }

            if (sleepTime > 2000L) {
                log.warn("Send {} alarm recovery message to {}:{}, wait {} milliseconds.", alarmMessages.size(),
                        alarmSetting.getTargetHost(), alarmSetting.getTargetPort(), sleepTime
                );
                cycle = 2000L;
            }
        }
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
            grpcClient.connect();
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
