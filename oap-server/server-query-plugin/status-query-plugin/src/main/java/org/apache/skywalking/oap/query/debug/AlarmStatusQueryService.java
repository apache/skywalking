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

package org.apache.skywalking.oap.query.debug;

import com.google.gson.Gson;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.alarm.AlarmModule;
import org.apache.skywalking.oap.server.core.alarm.AlarmRulesWatcherService;
import org.apache.skywalking.oap.server.core.alarm.AlarmStatusWatcherService;
import org.apache.skywalking.oap.server.core.alarm.provider.AlarmRulesWatcher;
import org.apache.skywalking.oap.server.core.alarm.provider.status.AlarmRuleDetail;
import org.apache.skywalking.oap.server.core.alarm.provider.status.AlarmRuleList;
import org.apache.skywalking.oap.server.core.alarm.provider.status.AlarmRunningContext;
import org.apache.skywalking.oap.server.core.alarm.provider.status.ClusterAlarmStatus;
import org.apache.skywalking.oap.server.core.alarm.provider.status.InstanceAlarmStatus;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClient;
import org.apache.skywalking.oap.server.core.remote.client.RemoteClientManager;
import org.apache.skywalking.oap.server.core.remote.client.SelfRemoteClient;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.AlarmRequest;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteServiceGrpc;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.StatusRequest;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.StatusResponse;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

@Slf4j
public class AlarmStatusQueryService implements Service {
    private final ModuleManager moduleManager;
    private final static Gson GSON = new Gson();
    private AlarmRulesWatcher alarmRulesWatcher;
    private AlarmStatusWatcherService alarmStatusWatcher;
    private RemoteClientManager remoteClientManager;

    public AlarmStatusQueryService(final ModuleManager manager) {
        this.moduleManager = manager;
    }

    private AlarmRulesWatcher getAlarmRulesWatcher() {
        if (alarmRulesWatcher == null) {
            alarmRulesWatcher = (AlarmRulesWatcher) moduleManager.find(AlarmModule.NAME)
                                                                 .provider().getService(AlarmRulesWatcherService.class);
        }
        return alarmRulesWatcher;
    }

    private AlarmStatusWatcherService getAlarmStatusWatcher() {
        if (alarmStatusWatcher == null) {
            alarmStatusWatcher = moduleManager.find(AlarmModule.NAME)
                                              .provider().getService(AlarmStatusWatcherService.class);
        }
        return alarmStatusWatcher;
    }

    private RemoteClientManager getRemoteClientManager() {
        if (remoteClientManager == null) {
            remoteClientManager = moduleManager.find(CoreModule.NAME)
                                               .provider()
                                               .getService(RemoteClientManager.class);
        }
        return remoteClientManager;
    }

    public ClusterAlarmStatus<InstanceAlarmStatus<AlarmRuleList>> getAlarmRules() {
        ClusterAlarmStatus<InstanceAlarmStatus<AlarmRuleList>> result = new ClusterAlarmStatus<>();
        List<RemoteClient> list = getRemoteClientManager().getRemoteClient();
        for (RemoteClient remoteClient : list) {
            String rulesInfo = null;
            String errorMsg = null;
            try {
                if (remoteClient instanceof SelfRemoteClient) {
                    rulesInfo = getAlarmStatusWatcher().getAlarmRules();
                } else {
                    // get from remote oap in the cluster
                    RemoteServiceGrpc.RemoteServiceBlockingStub stub = RemoteServiceGrpc.newBlockingStub(
                        remoteClient.getChannel());
                    AlarmRequest alarmRequest = AlarmRequest.newBuilder()
                                                            .setRequestType(AlarmRequest.RequestType.GET_ALARM_RULES)
                                                            .build();
                    StatusResponse statusResponse = stub.syncStatus(
                        StatusRequest.newBuilder().setAlarmRequest(alarmRequest).build());
                    rulesInfo = statusResponse.getAlarmStatus();
                }
            } catch (Exception e) {
                log.warn("Failed to get alarm rule list.", e);
                errorMsg = e.getMessage();
            }
            AlarmRuleList alarmRuleList = GSON.fromJson(rulesInfo, AlarmRuleList.class);
            InstanceAlarmStatus<AlarmRuleList> instanceAlarmStatus = new InstanceAlarmStatus<>();
            instanceAlarmStatus.setStatus(alarmRuleList);
            instanceAlarmStatus.setAddress(remoteClient.getAddress().toString());
            instanceAlarmStatus.setErrorMsg(errorMsg);
            result.getOapInstances().add(instanceAlarmStatus);
        }
        return result;
    }

    public ClusterAlarmStatus<InstanceAlarmStatus<AlarmRuleDetail>> getAlarmRuleById(String ruleId) {
        ClusterAlarmStatus<InstanceAlarmStatus<AlarmRuleDetail>> result = new ClusterAlarmStatus<>();
        List<RemoteClient> list = getRemoteClientManager().getRemoteClient();
        for (RemoteClient remoteClient : list) {
            String ruleDetail = null;
            String errorMsg = null;
            try {
                if (remoteClient instanceof SelfRemoteClient) {
                    ruleDetail = getAlarmStatusWatcher().getAlarmRuleById(ruleId);
                } else {
                    // get from remote oap in the cluster
                    RemoteServiceGrpc.RemoteServiceBlockingStub stub = RemoteServiceGrpc.newBlockingStub(
                        remoteClient.getChannel());
                    AlarmRequest alarmRequest = AlarmRequest.newBuilder()
                                                            .setRequestType(
                                                                AlarmRequest.RequestType.GET_ALARM_RULE_BY_ID)
                                                            .setRuleId(ruleId)
                                                            .build();
                    StatusResponse statusResponse = stub.syncStatus(
                        StatusRequest.newBuilder().setAlarmRequest(alarmRequest).build());
                    ruleDetail = statusResponse.getAlarmStatus();
                }
            } catch (Exception e) {
                log.warn("Failed to get alarm rule detail by ID: {}.", ruleId, e);
                errorMsg = e.getMessage();
            }

            AlarmRuleDetail alarmRuleDetail = GSON.fromJson(ruleDetail, AlarmRuleDetail.class);
            InstanceAlarmStatus<AlarmRuleDetail> instanceAlarmRuleDetail = new InstanceAlarmStatus<>();
            instanceAlarmRuleDetail.setStatus(alarmRuleDetail);
            instanceAlarmRuleDetail.setAddress(remoteClient.getAddress().toString());
            instanceAlarmRuleDetail.setErrorMsg(errorMsg);
            result.getOapInstances().add(instanceAlarmRuleDetail);
        }
        return result;
    }

    public ClusterAlarmStatus<InstanceAlarmStatus<AlarmRunningContext>> getAlarmRuleContext(String ruleId, String entityName) {
        ClusterAlarmStatus<InstanceAlarmStatus<AlarmRunningContext>> result = new ClusterAlarmStatus<>();
        List<RemoteClient> list = getRemoteClientManager().getRemoteClient();
        for (RemoteClient remoteClient : list) {
            String context = null;
            String errorMsg = null;
            try {
                if (remoteClient instanceof SelfRemoteClient) {
                    context = getAlarmStatusWatcher().getAlarmRuleContext(ruleId, entityName);
                } else {
                    // get from remote oap in the cluster
                    RemoteServiceGrpc.RemoteServiceBlockingStub stub = RemoteServiceGrpc.newBlockingStub(
                        remoteClient.getChannel());
                    AlarmRequest alarmRequest = AlarmRequest.newBuilder()
                                                            .setRequestType(
                                                                AlarmRequest.RequestType.GET_ALARM_RULE_CONTEXT)
                                                            .setRuleId(ruleId)
                                                            .setEntityName(entityName)
                                                            .build();
                    StatusResponse statusResponse = stub.syncStatus(
                        StatusRequest.newBuilder().setAlarmRequest(alarmRequest).build());
                    context = statusResponse.getAlarmStatus();
                }
            } catch (Exception e) {
                log.warn("Failed to get alarm running context by ruleId: {} and entityName: {}.", ruleId, entityName, e);
                errorMsg = e.getMessage();
            }
            AlarmRunningContext alarmRunningContext = GSON.fromJson(context, AlarmRunningContext.class);
            InstanceAlarmStatus<AlarmRunningContext> runningContext = new InstanceAlarmStatus<>();
            runningContext.setStatus(alarmRunningContext);
            runningContext.setAddress(remoteClient.getAddress().toString());
            runningContext.setErrorMsg(errorMsg);
            result.getOapInstances().add(runningContext);
        }
        return result;
    }
}
