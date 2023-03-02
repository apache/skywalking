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

package org.apache.skywalking.oap.server.receiver.ebpf.provider.handler;

import com.google.common.base.Functions;
import com.google.gson.Gson;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.ContinuousProfilingCause;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.ContinuousProfilingPolicyQuery;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.ContinuousProfilingReport;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.ContinuousProfilingServiceGrpc;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.ContinuousProfilingServicePolicyQuery;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamProcessor;
import org.apache.skywalking.oap.server.core.command.CommandService;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingMonitorType;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicy;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.input.EBPFNetworkDataCollectingSettings;
import org.apache.skywalking.oap.server.core.query.input.EBPFNetworkSamplingRule;
import org.apache.skywalking.oap.server.core.query.type.ContinuousProfilingSingleValueCause;
import org.apache.skywalking.oap.server.core.query.type.ContinuousProfilingTriggeredCause;
import org.apache.skywalking.oap.server.core.query.type.ContinuousProfilingURICause;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskContinuousProfiling;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskExtension;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.continuous.IContinuousProfilingPolicyDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.server.grpc.GRPCHandler;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.network.trace.component.command.ContinuousProfilingPolicyCommand;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
public class ContinuousProfilingServiceHandler extends ContinuousProfilingServiceGrpc.ContinuousProfilingServiceImplBase implements GRPCHandler {
    private static final Gson GSON = new Gson();

    private IContinuousProfilingPolicyDAO policyDAO;
    private final CommandService commandService;

    public ContinuousProfilingServiceHandler(ModuleManager moduleManager) {
        this.policyDAO = moduleManager.find(StorageModule.NAME).provider().getService(IContinuousProfilingPolicyDAO.class);
        this.commandService = moduleManager.find(CoreModule.NAME).provider().getService(CommandService.class);
    }

    @Override
    public void queryPolicies(ContinuousProfilingPolicyQuery request, StreamObserver<Commands> responseObserver) {
        final Map<String, String> policiesQuery = request.getPoliciesList().stream()
            .collect(Collectors.toMap(s -> IDManager.ServiceID.buildId(s.getServiceName(), true), ContinuousProfilingServicePolicyQuery::getUuid, (s1, s2) -> s1));
        if (CollectionUtils.isEmpty(policiesQuery)) {
            responseObserver.onNext(Commands.newBuilder().build());
            responseObserver.onCompleted();
            return;
        }

        try {
            final Map<String, ContinuousProfilingPolicy> policiesInDB = policyDAO.queryPolicies(new ArrayList<>(policiesQuery.keySet()))
                .stream().collect(Collectors.toMap(ContinuousProfilingPolicy::getServiceId, Functions.identity(), (s1, s2) -> s1));

            final ArrayList<ContinuousProfilingPolicy> updatePolicies = new ArrayList<>();
            for (Map.Entry<String, String> entry : policiesQuery.entrySet()) {
                final String serviceId = entry.getKey();

                final ContinuousProfilingPolicy policyInDB = policiesInDB.get(serviceId);
                // policy not exist in DB or uuid not same
                // needs to send commands to downstream
                if (policyInDB == null && StringUtil.isNotEmpty(entry.getValue())) {
                    final ContinuousProfilingPolicy emptyPolicy = new ContinuousProfilingPolicy();
                    emptyPolicy.setServiceId(entry.getKey());
                    emptyPolicy.setUuid("");
                    updatePolicies.add(emptyPolicy);
                } else if (policyInDB != null && !Objects.equals(policyInDB.getUuid(), entry.getValue())) {
                    updatePolicies.add(policyInDB);
                }
            }

            if (CollectionUtils.isEmpty(updatePolicies)) {
                sendEmptyCommands(responseObserver);
                return;
            }

            final ContinuousProfilingPolicyCommand command = commandService.newContinuousProfilingServicePolicyCommand(updatePolicies);
            final Commands.Builder builder = Commands.newBuilder();
            builder.addCommands(command.serialize());
            responseObserver.onNext(builder.build());
            responseObserver.onCompleted();
            return;
        } catch (Exception e) {
            log.warn("query continuous profiling service policies failure", e);
        }
        sendEmptyCommands(responseObserver);
    }

    private void sendEmptyCommands(StreamObserver<Commands> responseObserver) {
        responseObserver.onNext(Commands.newBuilder().build());
        responseObserver.onCompleted();
    }

    @Override
    public void reportProfilingTask(ContinuousProfilingReport request, StreamObserver<Commands> responseObserver) {
        // set up the task record
        final long currentTime = System.currentTimeMillis();
        final EBPFProfilingTaskRecord task = new EBPFProfilingTaskRecord();
        final String serviceId = IDManager.ServiceID.buildId(request.getServiceName(), true);
        final String instanceId = IDManager.ServiceInstanceID.buildId(serviceId, request.getInstanceName());
        final String processId = IDManager.ProcessID.buildId(instanceId, request.getProcessName());

        task.setServiceId(serviceId);
        task.setProcessLabelsJson(Const.EMPTY_STRING);
        task.setInstanceId(instanceId);
        task.setStartTime(currentTime);
        task.setTriggerType(EBPFProfilingTriggerType.CONTINUOUS_PROFILING.value());
        task.setFixedTriggerDuration(request.getDuration());
        task.setCreateTime(currentTime);
        task.setLastUpdateTime(currentTime);

        final EBPFProfilingTaskContinuousProfiling continuousProfiling = new EBPFProfilingTaskContinuousProfiling();
        continuousProfiling.setProcessId(processId);
        continuousProfiling.setProcessName(request.getProcessName());
        continuousProfiling.setCauses(request.getCausesList().stream().map(this::parseTaskCause).collect(Collectors.toList()));
        settingTargetTask(request, task, continuousProfiling);
        task.setContinuousProfilingJson(GSON.toJson(continuousProfiling));
        task.generateLogicalId();

        // save the profiling task
        NoneStreamProcessor.getInstance().in(task);

        final Commands.Builder builder = Commands.newBuilder();
        builder.addCommands(commandService.newContinuousProfilingReportCommand(task.getLogicalId()).serialize());
        responseObserver.onNext(builder.build());
        responseObserver.onCompleted();
    }

    private void settingTargetTask(ContinuousProfilingReport request, EBPFProfilingTaskRecord task, EBPFProfilingTaskContinuousProfiling continuousProfiling) {
        switch (request.getTargetTaskCase()) {
            case ONCPU:
                task.setTargetType(EBPFProfilingTargetType.ON_CPU.value());
                break;
            case OFFCPU:
                task.setTargetType(EBPFProfilingTargetType.OFF_CPU.value());
                break;
            case NETWORK:
                task.setTargetType(EBPFProfilingTargetType.NETWORK.value());
                final EBPFProfilingTaskExtension networkExtension = new EBPFProfilingTaskExtension();
                networkExtension.setNetworkSamplings(request.getNetwork().getSamplingURIRegexesList().stream().map(uri -> {
                    final EBPFNetworkSamplingRule rule = new EBPFNetworkSamplingRule();
                    rule.setMinDuration(0);
                    rule.setWhen4xx(true);
                    rule.setWhen5xx(true);
                    final EBPFNetworkDataCollectingSettings setting = new EBPFNetworkDataCollectingSettings();
                    setting.setRequireCompleteRequest(true);
                    setting.setRequireCompleteResponse(true);
                    rule.setSettings(setting);
                    return rule;
                }).collect(Collectors.toList()));
                task.setExtensionConfigJson(GSON.toJson(networkExtension));
                break;
            default:
                throw new IllegalArgumentException("the continuous profiling task type cannot recognized");
        }
    }

    private ContinuousProfilingTriggeredCause parseTaskCause(ContinuousProfilingCause cause) {
        final ContinuousProfilingTriggeredCause result = new ContinuousProfilingTriggeredCause();
        result.setType(ContinuousProfilingMonitorType.valueOf(cause.getType()));
        switch (cause.getCauseCase()) {
            case SINGLEVALUE:
                final ContinuousProfilingSingleValueCause singleValue = new ContinuousProfilingSingleValueCause();
                singleValue.setThreshold(thresholdToLong(cause.getSingleValue().getThreshold()));
                singleValue.setCurrent(thresholdToLong(cause.getSingleValue().getCurrent()));
                result.setSingleValue(singleValue);
                break;
            case URI:
                final ContinuousProfilingURICause uriCause = new ContinuousProfilingURICause();
                switch (cause.getUri().getUriCase()) {
                    case PATH:
                        uriCause.setUriPath(cause.getUri().getPath());
                        break;
                    case REGEX:
                        uriCause.setUriRegex(cause.getUri().getRegex());
                        break;
                    default:
                        throw new IllegalArgumentException("the uri case not set");
                }
                uriCause.setThreshold(thresholdToLong(cause.getUri().getThreshold()));
                uriCause.setCurrent(thresholdToLong(cause.getUri().getCurrent()));
                result.setUri(uriCause);
                break;
        }
        return result;
    }

    private long thresholdToLong(double val) {
        return (long) (val * 100);
    }

}