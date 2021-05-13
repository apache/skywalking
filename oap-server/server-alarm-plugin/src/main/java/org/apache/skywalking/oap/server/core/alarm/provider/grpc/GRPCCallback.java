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
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.alarm.AlarmCallback;
import org.apache.skywalking.oap.server.core.alarm.AlarmMessage;
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

    private AlarmRulesWatcher alarmRulesWatcher;

    private GRPCAlarmSetting alarmSetting;

    private AlarmServiceGrpc.AlarmServiceStub alarmServiceStub;

    private GRPCClient grpcClient;

    public GRPCCallback(AlarmRulesWatcher alarmRulesWatcher) {
        this.alarmRulesWatcher = alarmRulesWatcher;
        alarmSetting = alarmRulesWatcher.getGrpchookSetting();

        if (alarmSetting != null && !alarmSetting.isEmptySetting()) {
            grpcClient = new GRPCClient(alarmSetting.getTargetHost(), alarmSetting.getTargetPort());
            grpcClient.connect();
            alarmServiceStub = AlarmServiceGrpc.newStub(grpcClient.getChannel());
        }
    }

    @Override
    public void doAlarm(List<AlarmMessage> alarmMessage) {

        if (alarmSetting == null || alarmSetting.isEmptySetting()) {
            return;
        }

        // recreate gRPC client and stub if host and port configuration changed.
        onGRPCAlarmSettingUpdated(alarmRulesWatcher.getGrpchookSetting());

        GRPCStreamStatus status = new GRPCStreamStatus();

        if (alarmServiceStub == null) {
            return;
        }

        StreamObserver<org.apache.skywalking.oap.server.core.alarm.grpc.AlarmMessage> streamObserver =
            alarmServiceStub.withDeadlineAfter(10, TimeUnit.SECONDS).doAlarm(new StreamObserver<Response>() {
                @Override
                public void onNext(Response response) {
                    // ignore empty response
                }

                @Override
                public void onError(Throwable throwable) {
                    status.done();
                    if (log.isDebugEnabled()) {
                        log.debug("Send alarm message failed: {}", throwable.getMessage());
                    }
                }

                @Override
                public void onCompleted() {
                    status.done();
                    if (log.isDebugEnabled()) {
                        log.debug("Send alarm message successful.");
                    }
                }
            });

        alarmMessage.forEach(message -> {
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
                log.debug("Send {} alarm message to {}:{}.", alarmMessage.size(),
                          alarmSetting.getTargetHost(), alarmSetting.getTargetPort()
                );
            }

            if (sleepTime > 2000L) {
                log.warn("Send {} alarm message to {}:{}, wait {} milliseconds.", alarmMessage.size(),
                         alarmSetting.getTargetHost(), alarmSetting.getTargetPort(), sleepTime
                );
                cycle = 2000L;
            }
        }
    }

    private void onGRPCAlarmSettingUpdated(GRPCAlarmSetting grpcAlarmSetting) {
        if (grpcAlarmSetting == null) {
            if (grpcClient != null) {
                grpcClient.shutdown();
            }
            alarmServiceStub = null;
            alarmSetting = null;

            log.warn("gRPC alarm hook settings about host is empty, shutdown the old gRPC client.");
            return;
        }

        if (!grpcAlarmSetting.equals(alarmSetting)) {
            if (grpcClient != null) {
                grpcClient.shutdown();
            }
            grpcClient = new GRPCClient(grpcAlarmSetting.getTargetHost(), grpcAlarmSetting.getTargetPort());
            grpcClient.connect();
            alarmServiceStub = AlarmServiceGrpc.newStub(grpcClient.getChannel());
        }
    }
}
