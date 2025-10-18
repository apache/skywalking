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

package org.apache.skywalking.oap.server.core.profiling.pprof;

import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofDataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.pprof.IPprofTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.query.PprofTaskLog;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.PprofTask;
import java.util.Objects;
import org.apache.skywalking.oap.server.core.profiling.pprof.storage.PprofProfilingDataRecord;
import java.io.IOException;
import org.apache.skywalking.oap.server.library.pprof.type.FrameTree;
import org.apache.skywalking.oap.server.core.query.type.PprofStackTree;
import org.apache.skywalking.oap.server.library.pprof.parser.PprofMergeBuilder;
import java.util.List;
import com.google.gson.Gson;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PprofQueryService implements Service {
    private static final Gson GSON = new Gson();

    private final ModuleManager moduleManager;

    private IPprofTaskQueryDAO taskQueryDAO;
    private IPprofDataQueryDAO dataQueryDAO;
    private IPprofTaskLogQueryDAO logQueryDAO;

    private IPprofTaskQueryDAO getTaskQueryDAO() {
        if (taskQueryDAO == null) {
            this.taskQueryDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IPprofTaskQueryDAO.class);
        }
        return taskQueryDAO;
    }

    private IPprofDataQueryDAO getPprofDataQueryDAO() {
        if (dataQueryDAO == null) {
            this.dataQueryDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IPprofDataQueryDAO.class);
        }
        return dataQueryDAO;
    }

    private IPprofTaskLogQueryDAO getTaskLogQueryDAO() {
        if (logQueryDAO == null) {
            this.logQueryDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IPprofTaskLogQueryDAO.class);
        }
        return logQueryDAO;
    }

    public List<PprofTask> queryTask(String serviceId, Duration duration, Integer limit) throws IOException {
        Long startTimeBucket = null;
        Long endTimeBucket = null;
        if (Objects.nonNull(duration)) {
            startTimeBucket = duration.getStartTimeBucketInSec();
            endTimeBucket = duration.getEndTimeBucketInSec();
        }
        List<PprofTask> tasks = getTaskQueryDAO().getTaskList(serviceId, startTimeBucket, endTimeBucket, limit);
        return tasks;
    }

    public PprofStackTree queryPprofData(String taskId, List<String> instanceIds) throws IOException {
        List<PprofProfilingDataRecord> pprofDataList = getPprofDataQueryDAO().getByTaskIdAndInstances(taskId, instanceIds);
        List<FrameTree> trees = pprofDataList.stream()
                .map(data -> GSON.fromJson(new String(data.getDataBinary()), FrameTree.class))
                .collect(Collectors.toList());
        FrameTree resultTree = new PprofMergeBuilder()
                .merge(trees)
                .build();
        return new PprofStackTree(resultTree);
    }

    public List<PprofTaskLog> queryPprofTaskLogs(String taskId) throws IOException {
        List<PprofTaskLog> taskLogList = getTaskLogQueryDAO().getTaskLogList();
        return findMatchedLogs(taskId, taskLogList);
    }
    
    private List<PprofTaskLog> findMatchedLogs(final String taskID, final List<PprofTaskLog> allLogs) {
        return allLogs.stream()
                .filter(l -> Objects.equals(l.getId(), taskID))
                .map(this::extendTaskLog)
                .collect(Collectors.toList());
    }

    private PprofTaskLog extendTaskLog(PprofTaskLog log) {
        final IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition = IDManager.ServiceInstanceID
                .analysisId(log.getInstanceId());
        log.setInstanceName(instanceIDDefinition.getName());
        return log;
    }
    
}
