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

package org.apache.skywalking.oap.server.core.profiling.continuous;

import com.google.gson.Gson;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicy;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicyConfiguration;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTargetType;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTaskRecord;
import org.apache.skywalking.oap.server.core.profiling.ebpf.storage.EBPFProfilingTriggerType;
import org.apache.skywalking.oap.server.core.query.type.ContinuousProfilingMonitoringInstance;
import org.apache.skywalking.oap.server.core.query.type.ContinuousProfilingMonitoringProcess;
import org.apache.skywalking.oap.server.core.query.type.ContinuousProfilingPolicyItem;
import org.apache.skywalking.oap.server.core.query.type.ContinuousProfilingPolicyTarget;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTaskContinuousProfiling;
import org.apache.skywalking.oap.server.core.query.type.Process;
import org.apache.skywalking.oap.server.core.query.type.ServiceInstance;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.continuous.IContinuousProfilingPolicyDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.core.storage.query.IMetadataQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;

@Slf4j
@RequiredArgsConstructor
public class ContinuousProfilingQueryService implements Service {
    private static final Gson GSON = new Gson();
    private static final int RECENT_TRIGGERED_HOURS = 48;

    private final ModuleManager moduleManager;

    private IContinuousProfilingPolicyDAO policyDAO;
    private IMetadataQueryDAO metadataQueryDAO;
    private IEBPFProfilingTaskDAO ebpfProfilingTaskDAO;

    public IContinuousProfilingPolicyDAO getPolicyDAO() {
        if (policyDAO == null) {
            this.policyDAO = moduleManager.find(StorageModule.NAME)
                .provider().getService(IContinuousProfilingPolicyDAO.class);
        }
        return policyDAO;
    }

    public IMetadataQueryDAO getMetadataQueryDAO() {
        if (metadataQueryDAO == null) {
            this.metadataQueryDAO = moduleManager.find(StorageModule.NAME)
                .provider().getService(IMetadataQueryDAO.class);
        }
        return metadataQueryDAO;
    }

    public IEBPFProfilingTaskDAO getEbpfProfilingTaskDAO() {
        if (ebpfProfilingTaskDAO == null) {
            this.ebpfProfilingTaskDAO = moduleManager.find(StorageModule.NAME)
                .provider().getService(IEBPFProfilingTaskDAO.class);
        }
        return ebpfProfilingTaskDAO;
    }

    public List<ContinuousProfilingPolicyTarget> queryContinuousProfilingServiceTargets(String serviceId) throws IOException {
        final List<ContinuousProfilingPolicy> policies = getPolicyDAO().queryPolicies(Arrays.asList(serviceId));
        if (CollectionUtils.isEmpty(policies)) {
            return Collections.emptyList();
        }

        final ContinuousProfilingPolicy policy = policies.get(0);
        final ContinuousProfilingPolicyConfiguration configuration =
            ContinuousProfilingPolicyConfiguration.parseFromJSON(policy.getConfigurationJson());

        final List<EBPFProfilingTaskRecord> records = queryRecentTriggeredTasks(serviceId, configuration.getTargetCheckers().keySet());
        final Map<Integer, EBPFProfilingTaskSummary> summaryMap = buildSummaryByKey(records, EBPFProfilingTaskRecord::getTargetType);

        return configuration.getTargetCheckers().entrySet().stream().map(targetEntry -> {
            final ContinuousProfilingTargetType type = targetEntry.getKey();
            final List<ContinuousProfilingPolicyItem> items = targetEntry.getValue().entrySet().stream().map(checker -> {
                final ContinuousProfilingPolicyItem result = new ContinuousProfilingPolicyItem();
                final ContinuousProfilingPolicyConfiguration.CheckItem item = checker.getValue();
                result.setType(checker.getKey());
                result.setThreshold(item.getThreshold());
                result.setPeriod(item.getPeriod());
                result.setCount(item.getCount());
                result.setUriList(item.getUriList());
                result.setUriRegex(item.getUriRegex());
                return result;
            }).collect(Collectors.toList());

            final ContinuousProfilingPolicyTarget target = ContinuousProfilingPolicyTarget.builder()
                .type(type)
                .checkItems(items)
                .build();

            Optional.ofNullable(summaryMap.get(EBPFProfilingTargetType.valueOf(type).value()))
                .ifPresent(summary -> {
                    target.setTriggeredCount(summary.getCount());
                    target.setLastTriggerTimestamp(summary.getLastTriggerTime());
                });
            return target;
        }).collect(Collectors.toList());
    }

    public List<ContinuousProfilingMonitoringInstance> queryContinuousProfilingMonitoringInstances(String serviceId, ContinuousProfilingTargetType target) throws IOException {
        // Query all processes of the given service
        final List<Process> processes = getMetadataQueryDAO().listProcesses(serviceId, null,
            TimeBucket.getTimeBucket(calcLastTriggeredStartTime().getTimeInMillis(), DownSampling.Minute), 0);
        if (CollectionUtils.isEmpty(processes)) {
            return Collections.emptyList();
        }
        // query all triggered tasks
        final List<EBPFProfilingTaskRecord> records = queryRecentTriggeredTasks(serviceId, List.of(target));

        // Query the metadata of instances
        final Map<String, List<Process>> instancesProcesses = processes.stream().collect(Collectors.groupingBy(Process::getInstanceId));
        final List<ServiceInstance> instanceIdWithMetadata = getMetadataQueryDAO().getInstances(Arrays.asList(instancesProcesses.keySet().toArray(new String[0])));

        // Build instance & process summary
        final Map<String, EBPFProfilingTaskSummary> instanceSummary = buildSummaryByKey(records, EBPFProfilingTaskRecord::getInstanceId);
        final Map<String, EBPFProfilingTaskSummary> processSummary = buildSummaryByKey(records, r -> {
            final EBPFProfilingTaskContinuousProfiling continuousProfiling = GSON.fromJson(r.getContinuousProfilingJson(), EBPFProfilingTaskContinuousProfiling.class);
            return continuousProfiling.getProcessId();
        });

        // build result
        return instanceIdWithMetadata.stream().map(instance -> {
            final ContinuousProfilingMonitoringInstance result = new ContinuousProfilingMonitoringInstance();
            result.setId(instance.getId());
            result.setName(instance.getName());
            result.setAttributes(instance.getAttributes());
            final EBPFProfilingTaskSummary summary = instanceSummary.get(instance.getId());
            if (summary != null) {
                result.setTriggeredCount(summary.getCount());
                result.setLastTriggerTimestamp(summary.getLastTriggerTime());
            }

            result.setProcesses(instancesProcesses.getOrDefault(instance.getId(), List.of())
                .stream().map(p -> {
                    final ContinuousProfilingMonitoringProcess process = new ContinuousProfilingMonitoringProcess();
                    process.setId(p.getId());
                    process.setName(p.getName());
                    process.setDetectType(p.getDetectType());
                    process.setLabels(p.getLabels());

                    final EBPFProfilingTaskSummary processSummaryItem = processSummary.get(p.getId());
                    if (processSummaryItem != null) {
                        process.setTriggeredCount(processSummaryItem.getCount());
                        process.setLastTriggerTimestamp(processSummaryItem.getLastTriggerTime());
                    }

                    return process;
                }).collect(Collectors.toList()));
            return result;
        }).collect(Collectors.toList());
    }

    private <T> Map<T, EBPFProfilingTaskSummary> buildSummaryByKey(List<EBPFProfilingTaskRecord> records, Function<EBPFProfilingTaskRecord, T> groupBy) {
        return records.stream().collect(Collectors.groupingByConcurrent(groupBy, buildSummaryCollector()));
    }

    private List<EBPFProfilingTaskRecord> queryRecentTriggeredTasks(String serviceId, Collection<ContinuousProfilingTargetType> targets) throws IOException {
        return getEbpfProfilingTaskDAO().queryTasksByTargets(serviceId, null,
            targets.stream().map(EBPFProfilingTargetType::valueOf).collect(Collectors.toList()),
            EBPFProfilingTriggerType.CONTINUOUS_PROFILING, calcLastTriggeredStartTime().getTimeInMillis(), 0);
    }

    private Calendar calcLastTriggeredStartTime() {
        final Calendar timeInstance = Calendar.getInstance();
        timeInstance.add(Calendar.HOUR, -RECENT_TRIGGERED_HOURS);
        return timeInstance;
    }

    /**
     * Summary all records to one summary
     */
    private Collector<EBPFProfilingTaskRecord, EBPFProfilingTaskSummary, EBPFProfilingTaskSummary> buildSummaryCollector() {
        return Collector.of(EBPFProfilingTaskSummary::new,
            (result, task) -> {
                result.setCount(result.getCount() + 1);
                if (task.getStartTime() > result.getLastTriggerTime()) {
                    result.setLastTriggerTime(task.getStartTime());
                }
                result.getRecords().add(task);
            },
            (result1, result2) -> {
                result1.setCount(result1.getCount() + result2.getCount());
                if (result2.getLastTriggerTime() > result1.getLastTriggerTime()) {
                    result1.setLastTriggerTime(result2.getLastTriggerTime());
                }
                result1.getRecords().addAll(result2.getRecords());
                return result1;
            });
    }

    @Data
    private static class EBPFProfilingTaskSummary {
        // count of triggered tasks
        private int count;
        // last trigger time
        private long lastTriggerTime;
        // all triggered tasks
        private List<EBPFProfilingTaskRecord> records;

        public EBPFProfilingTaskSummary() {
            this.records = new ArrayList<>();
        }
    }

}
