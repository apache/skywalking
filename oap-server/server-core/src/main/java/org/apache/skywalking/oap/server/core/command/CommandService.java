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

package org.apache.skywalking.oap.server.core.command;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicy;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicyConfiguration;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerEventType;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskExtension;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.network.trace.component.command.AsyncProfilerTaskCommand;
import org.apache.skywalking.oap.server.network.trace.component.command.ContinuousProfilingReportCommand;
import org.apache.skywalking.oap.server.network.trace.component.command.ContinuousProfilingPolicyCommand;
import org.apache.skywalking.oap.server.network.trace.component.command.EBPFProfilingTaskCommand;
import org.apache.skywalking.oap.server.network.trace.component.command.EBPFProfilingTaskExtensionConfig;
import org.apache.skywalking.oap.server.network.trace.component.command.PprofTaskCommand;
import org.apache.skywalking.oap.server.network.trace.component.command.ProfileTaskCommand;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * CommandService represents the command creation factory. All commands for downstream agents should be created here.
 */
public class CommandService implements Service {
    private static final Gson GSON = new Gson();
    private final ModuleManager moduleManager;

    public CommandService(final ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    public ProfileTaskCommand newProfileTaskCommand(ProfileTask task) {
        final String serialNumber = UUID.randomUUID().toString();
        return new ProfileTaskCommand(
            serialNumber, task.getId(), task.getEndpointName(), task.getDuration(), task.getMinDurationThreshold(), task
            .getDumpPeriod(), task.getMaxSamplingCount(), task.getStartTime(), task.getCreateTime());
    }

    public AsyncProfilerTaskCommand newAsyncProfileTaskCommand(AsyncProfilerTask task) {
        final String serialNumber = UUID.randomUUID().toString();
        List<String> eventNames = task.getEvents().stream()
                .map(AsyncProfilerEventType::getName)
                .collect(Collectors.toList());
        return new AsyncProfilerTaskCommand(serialNumber, task.getId(), task.getDuration(),
                eventNames, task.getExecArgs(), task.getCreateTime());
    }

    /**
     * Create a new pprof task command for Go agents
     */
    public PprofTaskCommand newPprofTaskCommand(PprofTask task) {
        final String serialNumber = UUID.randomUUID().toString();
        String events = "";
        if (task.getEvents() != null) {
            events = task.getEvents().getName();
        }
        return new PprofTaskCommand(serialNumber, task.getId(), events, 
                task.getDuration(), task.getCreateTime(), task.getDumpPeriod());
    }

    /**
     * Used to notify the eBPF Profiling task to the eBPF agent side
     */
    public EBPFProfilingTaskCommand newEBPFProfilingTaskCommand(EBPFProfilingTaskRecord task, List<String> processId) {
        final String serialNumber = UUID.randomUUID().toString();
        EBPFProfilingTaskCommand.FixedTrigger fixedTrigger = null;
        if (Objects.equals(task.getTriggerType(), EBPFProfilingTriggerType.FIXED_TIME.value())) {
            fixedTrigger = new EBPFProfilingTaskCommand.FixedTrigger(task.getFixedTriggerDuration());
        }
        return new EBPFProfilingTaskCommand(serialNumber, task.getLogicalId(), processId, task.getStartTime(),
            task.getLastUpdateTime(), EBPFProfilingTriggerType.valueOf(task.getTriggerType()).name(), fixedTrigger,
            EBPFProfilingTargetType.valueOf(task.getTargetType()).name(),
            convertExtension(task));
    }

    public ContinuousProfilingPolicyCommand newContinuousProfilingServicePolicyCommand(List<ContinuousProfilingPolicy> policy) {
        return new ContinuousProfilingPolicyCommand(UUID.randomUUID().toString(),
            policy.stream().map(this::convertContinuesProfilingPolicy).collect(Collectors.toList()));
    }

    public ContinuousProfilingReportCommand newContinuousProfilingReportCommand(String taskId) {
        return new ContinuousProfilingReportCommand(UUID.randomUUID().toString(), taskId);
    }

    private org.apache.skywalking.oap.server.network.trace.component.command.ContinuousProfilingPolicy convertContinuesProfilingPolicy(ContinuousProfilingPolicy policy) {
        final org.apache.skywalking.oap.server.network.trace.component.command.ContinuousProfilingPolicy result = new org.apache.skywalking.oap.server.network.trace.component.command.ContinuousProfilingPolicy();
        result.setServiceName(IDManager.ServiceID.analysisId(policy.getServiceId()).getName());
        result.setUuid(policy.getUuid());
        if (StringUtil.isNotEmpty(policy.getConfigurationJson())) {
            final ContinuousProfilingPolicyConfiguration configuration = ContinuousProfilingPolicyConfiguration.parseFromJSON(policy.getConfigurationJson());
            result.setProfiling(configuration.getTargetCheckers().entrySet().stream().collect(Collectors.toMap(
                c -> c.getKey().name(),
                c -> c.getValue().entrySet().stream().collect(Collectors.toMap(i -> i.getKey().name(), i -> {
                        final org.apache.skywalking.oap.server.network.trace.component.command.ContinuousProfilingPolicy.Item item = new org.apache.skywalking.oap.server.network.trace.component.command.ContinuousProfilingPolicy.Item();
                        item.setThreshold(i.getValue().getThreshold());
                        item.setPeriod(i.getValue().getPeriod());
                        item.setCount(i.getValue().getCount());
                        item.setUriList(i.getValue().getUriList());
                        item.setUriRegex(i.getValue().getUriRegex());
                        return item;
                    }
                )))));
        }
        return result;
    }

    private EBPFProfilingTaskExtensionConfig convertExtension(EBPFProfilingTaskRecord task) {
        if (StringUtil.isEmpty(task.getExtensionConfigJson())) {
            return null;
        }
        EBPFProfilingTaskExtension extensionConfig = GSON.fromJson(task.getExtensionConfigJson(), EBPFProfilingTaskExtension.class);
        if (CollectionUtils.isEmpty(extensionConfig.getNetworkSamplings())) {
            return null;
        }
        EBPFProfilingTaskExtensionConfig config = new EBPFProfilingTaskExtensionConfig();
        config.setNetworkSamplings(extensionConfig.getNetworkSamplings().stream().map(s -> {
            return EBPFProfilingTaskExtensionConfig.NetworkSamplingRule.builder()
                    .uriRegex(s.getUriRegex())
                    .minDuration(s.getMinDuration())
                    .when4xx(s.isWhen5xx())
                    .when5xx(s.isWhen5xx())
                    .settings(EBPFProfilingTaskExtensionConfig.CollectSettings.builder()
                            .requireCompleteRequest(s.getSettings().isRequireCompleteRequest())
                            .maxRequestSize(s.getSettings().getMaxRequestSize())
                            .requireCompleteResponse(s.getSettings().isRequireCompleteResponse())
                            .maxResponseSize(s.getSettings().getMaxResponseSize())
                            .build())
                    .build();
        }).collect(Collectors.toList()));

        return config;
    }

}
