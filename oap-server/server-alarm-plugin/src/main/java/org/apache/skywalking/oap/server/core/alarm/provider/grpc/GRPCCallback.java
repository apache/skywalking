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
import org.apache.skywalking.oap.server.core.alarm.grpc.Response;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.exporter.ExportStatus;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;

/**
 * Use SkyWalking alarm grpc API call a remote methods.
 */
@Slf4j
public class GRPCCallback implements AlarmCallback {

    private GRPCAlarmSetting alarmSetting;

    private AlarmServiceGrpc.AlarmServiceStub alarmServiceStub;

    private GRPCClient grpcClient;

    public GRPCCallback(AlarmRulesWatcher alarmRulesWatcher) {
        alarmSetting = alarmRulesWatcher.getGrpchookSetting();
    }

    @Override
    public void doAlarm(List<AlarmMessage> alarmMessage) {

        if (alarmSetting == null) {
            return;
        }

        grpcClient = new GRPCClient(alarmSetting.getTargetHost(), alarmSetting.getTargetPort());
        grpcClient.connect();
        alarmServiceStub = AlarmServiceGrpc.newStub(grpcClient.getChannel());

        ExportStatus status = new ExportStatus();

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

            streamObserver.onNext(builder.build());
        });

        streamObserver.onCompleted();

        long sleepTime = 0;
        long cycle = 100L;
        /**
         * For memory safe of oap, we must wait for the peer confirmation.
         */
        while (!status.isDone()) {
            try {
                sleepTime += cycle;
                Thread.sleep(cycle);
            } catch (InterruptedException e) {
            }

            if (log.isDebugEnabled()) {
                log.debug("Send {} alarm message to {}:{}.", alarmMessage.size(),
                          alarmSetting.getTargetHost(), alarmSetting.getTargetPort()
                );
            }

            if (sleepTime > 2000L) {
                log.debug("Send {} alarm message to {}:{}, wait {} milliseconds.", alarmMessage.size(),
                          alarmSetting.getTargetHost(), alarmSetting.getTargetPort(), sleepTime
                );
                cycle = 2000L;
            }
        }
    }
}
