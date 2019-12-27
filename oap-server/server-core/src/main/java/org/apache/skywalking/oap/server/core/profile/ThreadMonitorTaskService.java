package org.apache.skywalking.oap.server.core.profile;

import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamingProcessor;
import org.apache.skywalking.oap.server.core.profile.entity.ThreadMonitorTaskCreateResult;
import org.apache.skywalking.oap.server.core.storage.StorageModule;
import org.apache.skywalking.oap.server.core.storage.profile.IThreadMonitorTaskQueryDAO;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.module.Service;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * thread monitor task service, handle task create, task query.(base on GraphQL)
 *
 * @author MrPro
 */
public class ThreadMonitorTaskService implements Service {

    private final ModuleManager moduleManager;
    private IThreadMonitorTaskQueryDAO threadMonitorTaskDAO;

    public ThreadMonitorTaskService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private IThreadMonitorTaskQueryDAO getThreadMonitorTaskDAO() {
        if (threadMonitorTaskDAO == null) {
            this.threadMonitorTaskDAO = moduleManager.find(StorageModule.NAME).provider().getService(IThreadMonitorTaskQueryDAO.class);
        }
        return threadMonitorTaskDAO;
    }

    /**
     * create new thread monitor task
     * @param serviceId monitor service id
     * @param endpointName monitor endpoint name
     * @param monitorStartTime create fix start time task when it's bigger 0
     * @param monitorDuration monitor task duration
     * @param minDurationThreshold min duration threshold
     * @param dumpPeriod dump period
     * @return task create result
     */
    public ThreadMonitorTaskCreateResult createTask(final int serviceId, final String endpointName, final long monitorStartTime, final int monitorDuration,
                                                    final int minDurationThreshold, final int dumpPeriod) {

        // calculate task execute range
        long taskStartTime = monitorStartTime > 0 ? monitorStartTime : System.currentTimeMillis();
        long taskEndTime = taskStartTime + TimeUnit.MINUTES.toMillis(monitorDuration);

        // check data
        final String errorMessage = checkDataSuccess(serviceId, endpointName, taskStartTime, taskEndTime, monitorDuration, minDurationThreshold, dumpPeriod);
        if (errorMessage != null) {
            return ThreadMonitorTaskCreateResult.builder().success(false).errorReason(errorMessage).build();
        }

        // create task
        final long createTime = System.currentTimeMillis();
        final ThreadMonitorTaskNoneStream task = new ThreadMonitorTaskNoneStream();
        task.setServiceId(serviceId);
        task.setEndpointName(endpointName.trim());
        task.setMonitorStartTime(monitorStartTime);
        task.setMonitorDuration(monitorDuration);
        task.setMinDurationThreshold(minDurationThreshold);
        task.setDumpPeriod(dumpPeriod);
        task.setCreateTime(createTime);
        task.setTimeBucket(TimeBucket.getRecordTimeBucket(taskEndTime));
        NoneStreamingProcessor.getInstance().in(task);

        return ThreadMonitorTaskCreateResult.builder().success(true).id(task.id()).build();
    }

    private String checkDataSuccess(final Integer serviceId, final String endpointName, final long monitorStartTime, final long monitorEndTime, final int monitorDuration,
                             final int minDurationThreshold, final int dumpPeriod) {
        // basic check
        if (serviceId == null) {
            return "service cannot be null";
        }
        if (StringUtil.isBlank(endpointName)) {
            return "endpoint name cannot be empty";
        }
        if (monitorEndTime - monitorStartTime > TimeUnit.MINUTES.toMillis(1)) {
            return "monitor duration must greater than 1 minutes";
        }
        if (monitorDuration <= 0) {
            return "monitor duration must greater than zero";
        }
        if (minDurationThreshold < 0) {
            return "min duration threshold must greater than or equals zero";
        }

        // check limit
        // The duration of the monitoring task cannot be greater than 15 minutes
        if (monitorDuration > 15) {
            return "The duration of the monitoring task cannot be greater than 15 minutes";
        }
        // dump period must be greater than or equals 10 milliseconds
        if (dumpPeriod < 10) {
            return "dump period must be greater than or equals 10 milliseconds";
        }
        // Each service can monitor up to 1 endpoints during the execution of tasks
        final List<ThreadMonitorTaskNoneStream> hasCreatedTask = getThreadMonitorTaskDAO().getHasTaskAlreadyCreate(serviceId, monitorStartTime, monitorEndTime);
        if (CollectionUtils.isNotEmpty(hasCreatedTask)) {
            return "current service already has monitor task execute at this time";
        }

        return null;
    }

}
