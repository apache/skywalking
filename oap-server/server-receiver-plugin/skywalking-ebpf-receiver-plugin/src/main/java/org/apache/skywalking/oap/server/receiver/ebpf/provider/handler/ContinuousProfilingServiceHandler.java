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

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import io.grpc.stub.StreamObserver;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.network.common.v3.Commands;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.ContinuousProfilingCause;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.ContinuousProfilingPolicyQuery;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.ContinuousProfilingReport;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.ContinuousProfilingServiceGrpc;
import org.apache.skywalking.apm.network.ebpf.profiling.v3.ContinuousProfilingServicePolicyQuery;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.CoreModule;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
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
import org.apache.skywalking.oap.server.receiver.ebpf.provider.EBPFReceiverModuleConfig;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class ContinuousProfilingServiceHandler extends ContinuousProfilingServiceGrpc.ContinuousProfilingServiceImplBase implements GRPCHandler {
    private static final Gson GSON = new Gson();

    private IContinuousProfilingPolicyDAO policyDAO;
    private final CommandService commandService;
    private final Cache<String, PolicyWrapper> policyCache;

    public ContinuousProfilingServiceHandler(ModuleManager moduleManager, EBPFReceiverModuleConfig config) {
        this.policyDAO = moduleManager.find(StorageModule.NAME).provider().getService(IContinuousProfilingPolicyDAO.class);
        this.commandService = moduleManager.find(CoreModule.NAME).provider().getService(CommandService.class);
        this.policyCache = CacheBuilder.newBuilder()
            .expireAfterWrite(config.getContinuousPolicyCacheTimeout(), TimeUnit.SECONDS)
            .build();
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
            final List<String> serviceIdList = new ArrayList<>(policiesQuery.keySet());
            final HashMap<String, ContinuousProfilingPolicy> policiesInDB = new HashMap<>();

            // query from the cache first
            for (ListIterator<String> serviceIdIt = serviceIdList.listIterator(); serviceIdIt.hasNext(); ) {
                final String serviceId = serviceIdIt.next();
                final PolicyWrapper wrapper = this.policyCache.getIfPresent(serviceId);
                if (wrapper == null) {
                    continue;
                }
                serviceIdIt.remove();

                if (wrapper.policy != null) {
                    policiesInDB.put(serviceId, wrapper.policy);
                }
            }

            // query the policies which not in the cache
            final List<ContinuousProfilingPolicy> queriedFromDB = policyDAO.queryPolicies(serviceIdList);
            for (ContinuousProfilingPolicy dbPolicy : queriedFromDB) {
                policiesInDB.put(dbPolicy.getServiceId(), dbPolicy);
                this.policyCache.put(dbPolicy.getServiceId(), new PolicyWrapper(dbPolicy));
                serviceIdList.remove(dbPolicy.getServiceId());
            }

            // Also add the cache if the service haven't policy
            for (String serviceId : serviceIdList) {
                this.policyCache.put(serviceId, new PolicyWrapper(null));
            }

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
        task.setTimeBucket(TimeBucket.getRecordTimeBucket(currentTime));

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
        final ContinuousProfilingMonitorType type = ContinuousProfilingMonitorType.valueOf(cause.getType());
        result.setType(type);
        String caseFormat = "";
        switch (cause.getCauseCase()) {
            case SINGLEVALUE:
                final ContinuousProfilingSingleValueCause singleValue = new ContinuousProfilingSingleValueCause();
                singleValue.setThreshold(thresholdToLong(cause.getSingleValue().getThreshold()));
                singleValue.setCurrent(thresholdToLong(cause.getSingleValue().getCurrent()));
                result.setSingleValue(singleValue);
                caseFormat = generateCauseString(type, cause.getSingleValue().getThreshold(), cause.getSingleValue().getCurrent());
                break;
            case URI:
                final ContinuousProfilingURICause uriCause = new ContinuousProfilingURICause();
                String urlCause;
                switch (cause.getUri().getUriCase()) {
                    case PATH:
                        urlCause = cause.getUri().getPath();
                        uriCause.setUriPath(cause.getUri().getPath());
                        break;
                    case REGEX:
                        urlCause = cause.getUri().getRegex();
                        uriCause.setUriRegex(cause.getUri().getRegex());
                        break;
                    default:
                        throw new IllegalArgumentException("the uri case not set");
                }
                uriCause.setThreshold(thresholdToLong(cause.getUri().getThreshold()));
                uriCause.setCurrent(thresholdToLong(cause.getUri().getCurrent()));
                result.setUri(uriCause);
                caseFormat = generateCauseString(type, cause.getUri().getThreshold(), cause.getUri().getCurrent());
                if (StringUtils.isNotEmpty(urlCause)) {
                    caseFormat += " on " + urlCause;
                }
                break;
        }
        result.setMessage(result.getType().name() + ": " + caseFormat);
        return result;
    }

    private String generateCauseString(ContinuousProfilingMonitorType type, double threshold, double current) {
        NumberFormat percentInstance = NumberFormat.getPercentInstance();
        percentInstance.setMinimumFractionDigits(2);
        switch (type) {
            case HTTP_ERROR_RATE:
            case PROCESS_CPU:
                return String.format("current %s >= threshold %s", percentInstance.format(current / 100), percentInstance.format(threshold / 100));
            case PROCESS_THREAD_COUNT:
            case SYSTEM_LOAD:
                return String.format("current %d >= threshold %d", (int) current, (int) threshold);
            case HTTP_AVG_RESPONSE_TIME:
                return String.format("current %.2fms >= threshold %.2fms", current, threshold);
        }
        return "";
    }

    private long thresholdToLong(double val) {
        return (long) (val * 100);
    }

    @AllArgsConstructor
    private static class PolicyWrapper {
        final ContinuousProfilingPolicy policy;
    }
}