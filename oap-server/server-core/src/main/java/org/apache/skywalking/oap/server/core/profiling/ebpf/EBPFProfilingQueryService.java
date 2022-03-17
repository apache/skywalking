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
import org.apache.skywalking.oap.server.core.profiling.ebpf.analyze.EBPFProfilingAnalyzer;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.EBPFProfilingCondition;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzation;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingAnalyzeTimeRange;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingSchedule;
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingTask;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.EBPFProfilingProcessFinder;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingDataDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingScheduleDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IEBPFProfilingTaskDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

@RequiredArgsConstructor
public class EBPFProfilingQueryService implements Service {
    private final ModuleManager moduleManager;

    private IEBPFProfilingTaskDAO taskDAO;
    private IEBPFProfilingScheduleDAO scheduleDAO;
    private IEBPFProfilingDataDAO dataDAO;
    private EBPFProfilingAnalyzer profilingAnalyzer;

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

    private EBPFProfilingAnalyzer getProfilingAnalyzer() {
        if (profilingAnalyzer == null) {
            this.profilingAnalyzer = new EBPFProfilingAnalyzer(moduleManager);
        }
        return profilingAnalyzer;
    }

    public List<EBPFProfilingTask> queryEBPFProfilingTasks(EBPFProfilingCondition condition) throws IOException {
        return getTaskDAO().queryTasks(EBPFProfilingProcessFinder.builder()
                        .finderType(condition.getFinderType())
                        .serviceId(condition.getServiceId())
                        .instanceId(condition.getInstanceId())
                        .processIdList(Arrays.asList(condition.getProcessId()))
                .build(), null, 0, 0);
    }

    public List<EBPFProfilingSchedule> queryEBPFProfilingSchedules(String taskId, Duration duration) throws IOException {
        return getScheduleDAO().querySchedules(taskId, duration.getStartTimeBucket(), duration.getEndTimeBucket());
    }

    public EBPFProfilingAnalyzation getEBPFProfilingAnalyzation(String taskId, List<EBPFProfilingAnalyzeTimeRange> timeRanges) throws IOException {
        return getProfilingAnalyzer().analyze(taskId, timeRanges);
    }
}