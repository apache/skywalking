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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessDetectType;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ProcessTraffic;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.profiling.ebpf.analyze.EBPFProfilingAnalyzer;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.EBPFProfilingTaskCondition;
import org.apache.skywalking.oap.server.core.query.type.Attribute;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzeTimeRange;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingSchedule;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.storage.IMetricsDAO;
import org.apache.skywalking.oap.server.core.storage.StorageDAO;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.model.Model;
import org.apache.skywalking.oap.server.core.storage.model.StorageModels;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.EBPFProfilingProcessFinder;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingScheduleDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class EBPFProfilingQueryService implements Service {
    private final ModuleManager moduleManager;
    private final CoreModuleConfig config;
    private final StorageModels storageModels;

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
            this.profilingAnalyzer = new EBPFProfilingAnalyzer(moduleManager, config.getMaxDurationOfAnalyzeEBPFProfiling(),
                    config.getMaxDurationOfQueryEBPFProfilingData(), config.getMaxThreadCountOfQueryEBPFProfilingData());
        }
        return profilingAnalyzer;
    }

    public List<EBPFProfilingTask> queryEBPFProfilingTasks(EBPFProfilingTaskCondition condition) throws IOException {
        return getTaskDAO().queryTasks(EBPFProfilingProcessFinder.builder()
                        .finderType(condition.getFinderType())
                        .serviceId(condition.getServiceId())
                        .instanceId(condition.getInstanceId())
                        .processIdList(Arrays.asList(condition.getProcessId()))
                .build(), null, 0, 0);
    }

    public List<EBPFProfilingSchedule> queryEBPFProfilingSchedules(String taskId, Duration duration) throws IOException {
        final List<EBPFProfilingSchedule> schedules = getScheduleDAO().querySchedules(taskId, duration.getStartTimeBucket(), duration.getEndTimeBucket());
        if (CollectionUtils.isNotEmpty(schedules)) {
            final Model processModel = getProcessModel();
            final List<Metrics> processMetrics = schedules.stream()
                    .map(EBPFProfilingSchedule::getProcessId).distinct().map(processId -> {
                        final ProcessTraffic p = new ProcessTraffic();
                        p.setProcessId(processId);
                        return p;
                    }).collect(Collectors.toList());
            final List<Metrics> processes = getProcessMetricsDAO().multiGet(processModel, processMetrics);

            final Map<String, Process> processMap = processes.stream()
                    .map(t -> (ProcessTraffic) t)
                    .collect(Collectors.toMap(Metrics::id, this::convertProcess));
            schedules.forEach(p -> p.setProcess(processMap.get(p.getProcessId())));
        }
        return schedules;
    }

    public EBPFProfilingAnalyzation getEBPFProfilingAnalyzation(String taskId, List<EBPFProfilingAnalyzeTimeRange> timeRanges) throws IOException {
        return getProfilingAnalyzer().analyze(taskId, timeRanges);
    }

    private Process convertProcess(ProcessTraffic traffic) {
        final Process process = new Process();
        process.setId(traffic.id());
        process.setName(traffic.getName());
        final String serviceId = traffic.getServiceId();
        process.setServiceId(serviceId);
        process.setServiceName(IDManager.ServiceID.analysisId(serviceId).getName());
        final String instanceId = traffic.getInstanceId();
        process.setInstanceId(instanceId);
        process.setInstanceName(IDManager.ServiceInstanceID.analysisId(instanceId).getName());
        process.setLayer(Layer.valueOf(traffic.getLayer()).name());
        process.setAgentId(traffic.getAgentId());
        process.setDetectType(ProcessDetectType.valueOf(traffic.getDetectType()).name());
        if (traffic.getProperties() != null) {
            for (String key : traffic.getProperties().keySet()) {
                process.getAttributes().add(new Attribute(key, traffic.getProperties().get(key).getAsString()));
            }
        }
        return process;
    }
}