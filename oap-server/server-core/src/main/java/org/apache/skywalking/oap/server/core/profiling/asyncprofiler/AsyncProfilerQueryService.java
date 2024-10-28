package org.apache.skywalking.oap.server.core.profiling.asyncprofiler;

import com.google.gson.Gson;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.analysis.IDManager;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage.JFRProfilingDataRecord;
import org.apache.skywalking.oap.server.core.query.AsyncProfilerTaskLog;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerStackTree;
import org.apache.skywalking.oap.server.core.query.type.AsyncProfilerTask;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskLogQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IAsyncProfilerTaskQueryDAO;
import org.apache.skywalking.oap.server.core.storage.profiling.asyncprofiler.IJFRDataQueryDAO;
import org.apache.skywalking.oap.server.library.jfr.parser.convert.FrameTree;
import org.apache.skywalking.oap.server.library.jfr.parser.convert.JfrMergeBuilder;
import org.apache.skywalking.oap.server.library.jfr.parser.type.event.JFREventType;
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

    private IJFRDataQueryDAO getJfrDataQueryDAO() {
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

    public AsyncProfilerStackTree queryJfrData(String taskId, List<String> instanceIds, JFREventType eventType) throws IOException {
        List<JFRProfilingDataRecord> jfrDataList = getJfrDataQueryDAO().getByTaskIdAndInstancesAndEvent(taskId, instanceIds, eventType.name());
        List<FrameTree> trees = jfrDataList.stream()
                .map(data -> GSON.fromJson(new String(data.getDataBinary()), FrameTree.class))
                .collect(Collectors.toList());
        FrameTree resultTree = new JfrMergeBuilder()
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
                .filter(l -> Objects.equals(l.getTaskId(), taskID))
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

