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

package org.apache.skywalking.oap.server.core.profiling.ebpf;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.profiling.ebpf.analyze.EBPFProfilingAnalyzer;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.enumeration.ProfilingSupportStatus;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzeAggregateType;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzeTimeRange;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingSchedule;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskContinuousProfiling;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskExtension;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskPrepare;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingScheduleDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IServiceLabelDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.StringUtil;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class EBPFProfilingQueryService implements Service {
    private static final Gson GSON = new Gson();

    private final ModuleManager moduleManager;
    private final CoreModuleConfig config;
    private final StorageModels storageModels;

    private IMetadataQueryDAO metadataQueryDAO;
    private IServiceLabelDAO serviceLabelDAO;
    private IEBPFProfilingTaskDAO taskDAO;
    private IEBPFProfilingScheduleDAO scheduleDAO;
    private EBPFProfilingAnalyzer profilingAnalyzer;
    private IMetricsDAO processMetricsDAO;
    private Model processTrafficModel;

    private IEBPFProfilingTaskDAO getTaskDAO() {
        if (taskDAO == null) {
            this.taskDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IEBPFProfilingTaskDAO.class);
        }
        return taskDAO;
    }

    private IEBPFProfilingScheduleDAO getScheduleDAO() {
        if (scheduleDAO == null) {
            this.scheduleDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IEBPFProfilingScheduleDAO.class);
        }
        return scheduleDAO;
    }

    private IMetricsDAO getProcessMetricsDAO() {
        if (processMetricsDAO == null) {
            final StorageDAO storageDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(StorageDAO.class);
            this.processMetricsDAO = storageDAO.newMetricsDao(new ProcessTraffic.Builder());
        }
        return processMetricsDAO;
    }

    private Model getProcessModel() {
        if (processTrafficModel == null) {
            for (Model model : this.storageModels.allModels()) {
                if (Objects.equals(model.getName(), ProcessTraffic.INDEX_NAME)) {
                    processTrafficModel = model;
                    break;
                }
            }
            if (processTrafficModel == null) {
                throw new IllegalStateException("could not found the process traffic model");
            }
        }
        return processTrafficModel;
    }

    private EBPFProfilingAnalyzer getProfilingAnalyzer() {
        if (profilingAnalyzer == null) {
            this.profilingAnalyzer = new EBPFProfilingAnalyzer(moduleManager, config.getMaxDurationOfQueryEBPFProfilingData(),
                    config.getMaxThreadCountOfQueryEBPFProfilingData());
        }
        return profilingAnalyzer;
    }

    public IMetadataQueryDAO getMetadataQueryDAO() {
        if (metadataQueryDAO == null) {
            metadataQueryDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IMetadataQueryDAO.class);
        }
        return metadataQueryDAO;
    }

    public IServiceLabelDAO getServiceLabelDAO() {
        if (serviceLabelDAO == null) {
            serviceLabelDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IServiceLabelDAO.class);
        }
        return serviceLabelDAO;
    }

    public EBPFProfilingTaskPrepare queryPrepareCreateEBPFProfilingTaskData(String serviceId) throws IOException {
        final EBPFProfilingTaskPrepare prepare = new EBPFProfilingTaskPrepare();
        // query process count in last 10 minutes
        final long endTimestamp = System.currentTimeMillis();
        final long startTimestamp = endTimestamp - TimeUnit.MINUTES.toMillis(10);
        final long processesCount = getMetadataQueryDAO().getProcessCount(serviceId,
                ProfilingSupportStatus.SUPPORT_EBPF_PROFILING, TimeBucket.getTimeBucket(startTimestamp, DownSampling.Minute),
                TimeBucket.getTimeBucket(endTimestamp, DownSampling.Minute));
        if (processesCount <= 0) {
            prepare.setCouldProfiling(false);
            prepare.setProcessLabels(Collections.emptyList());
            return prepare;
        }
        prepare.setCouldProfiling(true);
        final List<String> processLabels = getServiceLabelDAO().queryAllLabels(serviceId);
        if (processLabels != null && !processLabels.isEmpty()) {
            prepare.setProcessLabels(processLabels.stream().distinct().collect(Collectors.toList()));
        } else {
            prepare.setProcessLabels(Collections.emptyList());
        }
        return prepare;
    }

    public List<EBPFProfilingTask> queryEBPFProfilingTasks(String serviceId, String serviceInstanceId, List<EBPFProfilingTargetType> targets, EBPFProfilingTriggerType triggerType, Duration duration) throws IOException {
        if (CollectionUtils.isEmpty(targets)) {
            targets = Arrays.asList(EBPFProfilingTargetType.values());
        }
        long startTime = 0, endTime = 0;
        if (duration != null) {
            startTime = duration.getStartTimestamp();
            endTime = duration.getEndTimestamp();
        }
        final List<EBPFProfilingTaskRecord> tasks = getTaskDAO().queryTasksByTargets(serviceId, serviceInstanceId, targets, triggerType, startTime, endTime);
        // combine same id tasks
        final Map<String, EBPFProfilingTaskRecord> records = tasks.stream().collect(Collectors.toMap(EBPFProfilingTaskRecord::getLogicalId, Function.identity(), EBPFProfilingTaskRecord::combine));
        return records.values().stream().map(this::parseTask).sorted((o1, o2) -> -Long.compare(o1.getCreateTime(), o2.getCreateTime())).collect(Collectors.toList());
    }

    private EBPFProfilingTask parseTask(EBPFProfilingTaskRecord record) {
        final EBPFProfilingTask result = new EBPFProfilingTask();
        result.setTaskId(record.getLogicalId());
        result.setServiceId(record.getServiceId());
        result.setServiceName(IDManager.ServiceID.analysisId(record.getServiceId()).getName());
        if (StringUtil.isNotEmpty(record.getProcessLabelsJson())) {
            result.setProcessLabels(GSON.<List<String>>fromJson(record.getProcessLabelsJson(), ArrayList.class));
        } else {
            result.setProcessLabels(Collections.emptyList());
        }
        if (StringUtil.isNotEmpty(record.getInstanceId())) {
            result.setServiceInstanceId(record.getInstanceId());
            result.setServiceInstanceName(IDManager.ServiceInstanceID.analysisId(record.getInstanceId()).getName());
        }
        result.setTaskStartTime(record.getStartTime());
        result.setTriggerType(EBPFProfilingTriggerType.valueOf(record.getTriggerType()));
        result.setFixedTriggerDuration(record.getFixedTriggerDuration());
        result.setTargetType(EBPFProfilingTargetType.valueOf(record.getTargetType()));
        result.setCreateTime(record.getCreateTime());
        result.setLastUpdateTime(record.getLastUpdateTime());
        if (StringUtil.isNotEmpty(record.getExtensionConfigJson())) {
            result.setExtensionConfig(GSON.fromJson(record.getExtensionConfigJson(), EBPFProfilingTaskExtension.class));
        }
        if (StringUtil.isNotEmpty(record.getContinuousProfilingJson())) {
            final EBPFProfilingTaskContinuousProfiling continuousProfiling = GSON.fromJson(record.getContinuousProfilingJson(), EBPFProfilingTaskContinuousProfiling.class);
            result.setProcessId(continuousProfiling.getProcessId());
            result.setProcessName(continuousProfiling.getProcessName());
            result.setContinuousProfilingCauses(continuousProfiling.getCauses());
        }
        return result;
    }

    public List<EBPFProfilingSchedule> queryEBPFProfilingSchedules(String taskId) throws Exception {
        final List<EBPFProfilingSchedule> schedules = getScheduleDAO().querySchedules(taskId);

        log.info("schedules: {}", GSON.toJson(schedules));

        if (CollectionUtils.isNotEmpty(schedules)) {
            final Model processModel = getProcessModel();
            final List<Metrics> processMetrics = schedules.stream()
                    .map(EBPFProfilingSchedule::getProcessId).distinct().map(processId -> {
                        final ProcessTraffic p = new ProcessTraffic();
                        p.setProcessId(processId);
                        return p;
                    }).collect(Collectors.toList());
            final List<Metrics> processes = getProcessMetricsDAO().multiGet(processModel, processMetrics);

            log.info("processes: {}", GSON.toJson(processes));

            final Map<String, Process> processMap = processes.stream()
                                                                .map(t -> (ProcessTraffic) t)
                                                                .collect(Collectors.toMap(m -> m.id().build(), this::convertProcess));
            schedules.forEach(p -> p.setProcess(processMap.get(p.getProcessId())));
        }
        return schedules;
    }

    public EBPFProfilingAnalyzation getEBPFProfilingAnalyzation(List<String> scheduleIdList,
                                                                List<EBPFProfilingAnalyzeTimeRange> timeRanges,
                                                                EBPFProfilingAnalyzeAggregateType aggregateType) throws IOException {
        return getProfilingAnalyzer().analyze(scheduleIdList, timeRanges, aggregateType);
    }

    private Process convertProcess(ProcessTraffic traffic) {
        final Process process = new Process();
        process.setId(traffic.id().build());
        process.setName(traffic.getName());
        final String serviceId = traffic.getServiceId();
        process.setServiceId(serviceId);
        process.setServiceName(IDManager.ServiceID.analysisId(serviceId).getName());
        final String instanceId = traffic.getInstanceId();
        process.setInstanceId(instanceId);
        process.setInstanceName(IDManager.ServiceInstanceID.analysisId(instanceId).getName());
        process.setAgentId(traffic.getAgentId());
        process.setDetectType(ProcessDetectType.valueOf(traffic.getDetectType()).name());
        if (traffic.getProperties() != null) {
            for (String key : traffic.getProperties().keySet()) {
                process.getAttributes().add(new Attribute(key, traffic.getProperties().get(key).getAsString()));
            }
        }
        if (StringUtil.isNotEmpty(traffic.getLabelsJson())) {
            process.getLabels().addAll(GSON.<List<String>>fromJson(traffic.getLabelsJson(), ArrayList.class));
        }
        return process;
    }
}
