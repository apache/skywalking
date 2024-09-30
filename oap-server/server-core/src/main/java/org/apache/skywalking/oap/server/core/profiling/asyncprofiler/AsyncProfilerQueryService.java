package org.apache.skywalking.oap.server.core.profiling.asyncprofiler;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.AsyncProfilerTaskLogRecord;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.JfrProfilingDataRecord;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerStackTree;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.query.type.ProfileTaskLog;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IJfrDataQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.trace.IProfileTaskLogQueryDAO;
import org.apache.skywalking.oap.server.library.jfr.parser.convert.FrameTree;
import org.apache.skywalking.oap.server.library.jfr.parser.convert.JfrMergeBuilder;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.JfrEventType;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class AsyncProfilerQueryService implements Service {
    private static final Gson GSON = new Gson();

    private final ModuleManager moduleManager;

    private IAsyncProfilerTaskQueryDAO taskQueryDAO;
    private IJfrDataQueryDAO dataQueryDAO;
    private IProfileTaskLogQueryDAO logQueryDAO;

    private IAsyncProfilerTaskQueryDAO getTaskQueryDAO() {
        if (taskQueryDAO == null) {
            this.taskQueryDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IAsyncProfilerTaskQueryDAO.class);
        }
        return taskQueryDAO;
    }

    private IJfrDataQueryDAO getJfrDataQueryDAO() {
        if (dataQueryDAO == null) {
            this.dataQueryDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IJfrDataQueryDAO.class);
        }
        return dataQueryDAO;
    }

    private IProfileTaskLogQueryDAO getTaskLogQueryDAO() {
        if (logQueryDAO == null) {
            this.logQueryDAO = moduleManager.find(StorageModule.NAME)
                    .provider()
                    .getService(IProfileTaskLogQueryDAO.class);
        }
        return logQueryDAO;
    }

    public List<AsyncProfilerTask> queryTask(String serviceId, Long startTime, Long endTime, Integer limit) throws IOException {
        Long startTimeBucket = null;
        if (Objects.nonNull(startTime)) {
            startTimeBucket = TimeBucket.getMinuteTimeBucket(startTime);
        }

        Long endTimeBucket = null;
        if (Objects.nonNull(endTime)) {
            endTimeBucket = TimeBucket.getMinuteTimeBucket(endTime);
        }

        return getTaskQueryDAO().getTaskList(serviceId, startTimeBucket, endTimeBucket, limit);
    }

    public AsyncProfilerStackTree queryJfrData(String taskId, List<String> instanceIds, JfrEventType eventType) throws IOException {
        List<JfrProfilingDataRecord> jfrDataList = getJfrDataQueryDAO().getById(taskId, instanceIds, eventType.name());
        List<FrameTree> trees = jfrDataList.stream()
                .map(data -> GSON.fromJson(new String(data.getDataBinary()), FrameTree.class))
                .collect(Collectors.toList());
        FrameTree resultTree = new JfrMergeBuilder()
                .merge(trees)
                .build();
        return new AsyncProfilerStackTree(eventType, resultTree);
    }

    public List<ProfileTaskLog> queryAsyncProfilerTaskLogs(String taskId) throws IOException {
        List<ProfileTaskLog> taskLogList = getTaskLogQueryDAO().getTaskLogList();
        return findMatchedLogs(taskId, taskLogList);
    }

    private List<ProfileTaskLog> findMatchedLogs(final String taskID, final List<ProfileTaskLog> allLogs) {
        return allLogs.stream()
                .filter(l -> com.google.common.base.Objects.equal(l.getTaskId(), taskID))
                .map(this::extendTaskLog)
                .collect(Collectors.toList());
    }

    private ProfileTaskLog extendTaskLog(ProfileTaskLog log) {
        final IDManager.ServiceInstanceID.InstanceIDDefinition instanceIDDefinition = IDManager.ServiceInstanceID
                .analysisId(log.getInstanceId());
        log.setInstanceName(instanceIDDefinition.getName());
        return log;
    }
}

