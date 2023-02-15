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

import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskExtension;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.network.trace.component.command.EBPFProfilingTaskCommand;
import org.apache.skywalking.oap.server.network.trace.component.command.EBPFProfilingTaskExtensionConfig;
import org.apache.skywalking.oap.server.network.trace.component.command.ProfileTaskCommand;
import org.apache.skywalking.oap.server.core.query.type.ProfileTask;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * CommandService represents the command creation factory. All commands for downstream agents should be created here.
 */
public class CommandService implements Service {
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

    /**
     * Used to notify the eBPF Profiling task to the eBPF agent side
     */
    public EBPFProfilingTaskCommand newEBPFProfilingTaskCommand(EBPFProfilingTask task, List<String> processId) {
        final String serialNumber = UUID.randomUUID().toString();
        EBPFProfilingTaskCommand.FixedTrigger fixedTrigger = null;
        if (Objects.equals(task.getTriggerType(), EBPFProfilingTriggerType.FIXED_TIME)) {
            fixedTrigger = new EBPFProfilingTaskCommand.FixedTrigger(task.getFixedTriggerDuration());
        }
        return new EBPFProfilingTaskCommand(serialNumber, task.getTaskId(), processId, task.getTaskStartTime(),
                task.getLastUpdateTime(), task.getTriggerType().name(), fixedTrigger, task.getTargetType().name(),
                convertExtension(task));
    }

    private EBPFProfilingTaskExtensionConfig convertExtension(EBPFProfilingTask task) {
        EBPFProfilingTaskExtension extensionConfig = task.getExtensionConfig();
        if (extensionConfig == null || CollectionUtils.isEmpty(extensionConfig.getNetworkSamplings())) {
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
