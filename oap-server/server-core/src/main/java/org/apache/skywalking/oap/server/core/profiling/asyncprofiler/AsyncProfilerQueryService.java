/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.oap.server.core.profiling.asyncprofiler;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.JFRProfilingDataRecord;
import org.apache.skywalking.oap.server.core.query.AsyncProfilerTaskLog;
import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerStackTree;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IJFRDataQueryDAO;
import org.apache.skywalking.oap.server.library.jfr.parser.JFRMergeBuilder;
import org.apache.skywalking.oap.server.library.jfr.type.FrameTree;
import org.apache.skywalking.oap.server.library.jfr.type.JFREventType;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class AsyncProfilerQueryService implements Service {
    private static final Gson GSON = new Gson();

    private final ModuleManager moduleManager;

    private IAsyncProfilerTaskQueryDAO taskQueryDAO;
    private IJFRDataQueryDAO dataQueryDAO;
    private IAsyncProfilerTaskLogQueryDAO logQueryDAO;

    private IAsyncProfilerTaskQueryDAO getTaskQueryDAO() {
        if (taskQueryDAO == null) {
            this.taskQueryDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IAsyncProfilerTaskQueryDAO.class);
        }
        return taskQueryDAO;
    }

    private IJFRDataQueryDAO getJFRDataQueryDAO() {
        if (dataQueryDAO == null) {
            this.dataQueryDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IJFRDataQueryDAO.class);
        }
        return dataQueryDAO;
    }

    private IAsyncProfilerTaskLogQueryDAO getTaskLogQueryDAO() {
        if (logQueryDAO == null) {
            this.logQueryDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IAsyncProfilerTaskLogQueryDAO.class);
        }
        return logQueryDAO;
    }

    public List<AsyncProfilerTask> queryTask(String serviceId, Duration duration, Integer limit) throws IOException {
        Long startTimeBucket = null;
        Long endTimeBucket = null;
        if (Objects.nonNull(duration)) {
            startTimeBucket = duration.getStartTimeBucketInSec();
            endTimeBucket = duration.getEndTimeBucketInSec();
        }

        return getTaskQueryDAO().getTaskList(serviceId, startTimeBucket, endTimeBucket, limit);
    }

    public AsyncProfilerStackTree queryJFRData(String taskId, List<String> instanceIds, JFREventType eventType) throws IOException {
        List<JFRProfilingDataRecord> jfrDataList = getJFRDataQueryDAO().getByTaskIdAndInstancesAndEvent(taskId, instanceIds, eventType.name());
        List<FrameTree> trees = jfrDataList.stream()
                .map(data -> GSON.fromJson(new String(data.getDataBinary()), FrameTree.class))
                .collect(Collectors.toList());
        FrameTree resultTree = new JFRMergeBuilder()
                .merge(trees)
                .build();
        return new AsyncProfilerStackTree(eventType, resultTree);
    }

    public List<AsyncProfilerTaskLog> queryAsyncProfilerTaskLogs(String taskId) throws IOException {
        List<AsyncProfilerTaskLog> taskLogList = getTaskLogQueryDAO().getTaskLogList();
        return findMatchedLogs(taskId, taskLogList);
    }

    private List<AsyncProfilerTaskLog> findMatchedLogs(final String taskID, final List<AsyncProfilerTaskLog> allLogs) {
        return allLogs.stream()
                .filter(l -> Objects.equals(l.getId(), taskID))
                .map(this::extendTaskLog)
                .collect(Collectors.toList());
    }

    private AsyncProfilerTaskLog extendTaskLog(AsyncProfilerTaskLog log) {
        final IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition = IDManager.ServiceInstanceID
                .analysisId(log.getInstanceId());
        log.setInstanceName(instanceIDDefinition.getName());
        return log;
    }
}

