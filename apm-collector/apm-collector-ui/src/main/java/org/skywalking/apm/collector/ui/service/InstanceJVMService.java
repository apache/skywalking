package org.skywalking.apm.collector.ui.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Set;
import org.skywalking.apm.collector.core.framework.UnexpectedException;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;
import org.skywalking.apm.collector.ui.dao.ICpuMetricDAO;
import org.skywalking.apm.collector.ui.dao.IGCMetricDAO;
import org.skywalking.apm.collector.ui.dao.IInstPerformanceDAO;
import org.skywalking.apm.collector.ui.dao.IInstanceDAO;
import org.skywalking.apm.collector.ui.dao.IMemoryMetricDAO;
import org.skywalking.apm.collector.ui.dao.IMemoryPoolMetricDAO;
import org.skywalking.apm.network.proto.PoolType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class InstanceJVMService {

    private final Logger logger = LoggerFactory.getLogger(InstanceJVMService.class);

    private Gson gson = new Gson();

    public JsonObject getInstanceOsInfo(int instanceId) {
        IInstanceDAO instanceDAO = (IInstanceDAO)DAOContainer.INSTANCE.get(IInstanceDAO.class.getName());
        InstanceDataDefine.Instance instance = instanceDAO.getInstance(instanceId);
        if (ObjectUtils.isEmpty(instance)) {
            throw new UnexpectedException("instance id: " + instance + " not exist.");
        }

        JsonObject response = gson.fromJson(instance.getOsInfo(), JsonObject.class);
        return response;
    }

    public JsonObject getInstanceJvmMetric(int instanceId, Set<String> metricTypes, long timeBucket) {
        JsonObject metrics = new JsonObject();
        if (metricTypes.contains(MetricType.cpu.name())) {
            ICpuMetricDAO cpuMetricDAO = (ICpuMetricDAO)DAOContainer.INSTANCE.get(ICpuMetricDAO.class.getName());
            metrics.addProperty(MetricType.cpu.name(), cpuMetricDAO.getMetric(instanceId, timeBucket));
        } else if (metricTypes.contains(MetricType.gc.name())) {
            IGCMetricDAO gcMetricDAO = (IGCMetricDAO)DAOContainer.INSTANCE.get(IGCMetricDAO.class.getName());
            metrics.add(MetricType.gc.name(), gcMetricDAO.getMetric(instanceId, timeBucket));
        } else if (metricTypes.contains(MetricType.tps.name())) {
            IInstPerformanceDAO instPerformanceDAO = (IInstPerformanceDAO)DAOContainer.INSTANCE.get(IInstPerformanceDAO.class.getName());
            metrics.addProperty(MetricType.tps.name(), instPerformanceDAO.getMetric(instanceId, timeBucket));
        } else if (metricTypes.contains(MetricType.heapmemory.name())) {
            IMemoryMetricDAO memoryMetricDAO = (IMemoryMetricDAO)DAOContainer.INSTANCE.get(IMemoryMetricDAO.class.getName());
            metrics.add(MetricType.heapmemory.name(), memoryMetricDAO.getMetric(instanceId, timeBucket, true));
        } else if (metricTypes.contains(MetricType.nonheapmemory.name())) {
            IMemoryMetricDAO memoryMetricDAO = (IMemoryMetricDAO)DAOContainer.INSTANCE.get(IMemoryMetricDAO.class.getName());
            metrics.add(MetricType.heapmemory.name(), memoryMetricDAO.getMetric(instanceId, timeBucket, false));
        } else if (metricTypes.contains(MetricType.heappermgen.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.heappermgen.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, true, PoolType.PERMGEN_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.heapmetaspace.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.heapmetaspace.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, true, PoolType.METASPACE_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.heapnewgen.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.heapnewgen.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, true, PoolType.NEWGEN_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.heapoldgen.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.heapoldgen.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, true, PoolType.OLDGEN_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.heapsurvivor.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.heapsurvivor.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, true, PoolType.SURVIVOR_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.nonheappermgen.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.nonheappermgen.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, false, PoolType.PERMGEN_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.nonheapmetaspace.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.nonheapmetaspace.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, false, PoolType.METASPACE_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.nonheapnewgen.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.nonheapnewgen.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, false, PoolType.NEWGEN_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.nonheapoldgen.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.nonheapnewgen.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, false, PoolType.OLDGEN_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.nonheapsurvivor.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.nonheapsurvivor.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, false, PoolType.OLDGEN_USAGE_VALUE));
        } else {
            throw new UnexpectedException("unexpected metric type");
        }
        return metrics;
    }

    public JsonObject getInstanceJvmMetrics(int instanceId, Set<String> metricTypes, long startTimeBucket,
        long endTimeBucket) {
        JsonObject metrics = new JsonObject();
        if (metricTypes.contains(MetricType.cpu.name())) {
            ICpuMetricDAO cpuMetricDAO = (ICpuMetricDAO)DAOContainer.INSTANCE.get(ICpuMetricDAO.class.getName());
            metrics.add(MetricType.cpu.name(), cpuMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket));
        } else if (metricTypes.contains(MetricType.gc.name())) {
            IGCMetricDAO gcMetricDAO = (IGCMetricDAO)DAOContainer.INSTANCE.get(IGCMetricDAO.class.getName());
            metrics.add(MetricType.gc.name(), gcMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket));
        } else if (metricTypes.contains(MetricType.tps.name())) {
            IInstPerformanceDAO instPerformanceDAO = (IInstPerformanceDAO)DAOContainer.INSTANCE.get(IInstPerformanceDAO.class.getName());
            metrics.add(MetricType.tps.name(), instPerformanceDAO.getMetric(instanceId, startTimeBucket, endTimeBucket));
        } else if (metricTypes.contains(MetricType.heapmemory.name())) {
            IMemoryMetricDAO memoryMetricDAO = (IMemoryMetricDAO)DAOContainer.INSTANCE.get(IMemoryMetricDAO.class.getName());
            metrics.add(MetricType.heapmemory.name(), memoryMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, true));
        } else if (metricTypes.contains(MetricType.nonheapmemory.name())) {
            IMemoryMetricDAO memoryMetricDAO = (IMemoryMetricDAO)DAOContainer.INSTANCE.get(IMemoryMetricDAO.class.getName());
            metrics.add(MetricType.heapmemory.name(), memoryMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, false));
        } else if (metricTypes.contains(MetricType.heappermgen.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.heappermgen.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, true, PoolType.PERMGEN_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.heapmetaspace.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.heapmetaspace.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, true, PoolType.METASPACE_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.heapnewgen.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.heapnewgen.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, true, PoolType.NEWGEN_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.heapoldgen.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.heapoldgen.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, true, PoolType.OLDGEN_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.heapsurvivor.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.heapsurvivor.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, true, PoolType.SURVIVOR_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.nonheappermgen.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.nonheappermgen.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, false, PoolType.PERMGEN_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.nonheapmetaspace.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.nonheapmetaspace.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, false, PoolType.METASPACE_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.nonheapnewgen.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.nonheapnewgen.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, false, PoolType.NEWGEN_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.nonheapoldgen.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.nonheapnewgen.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, false, PoolType.OLDGEN_USAGE_VALUE));
        } else if (metricTypes.contains(MetricType.nonheapsurvivor.name())) {
            IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
            metrics.add(MetricType.nonheapsurvivor.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, false, PoolType.OLDGEN_USAGE_VALUE));
        } else {
            throw new UnexpectedException("unexpected metric type");
        }
        return metrics;
    }

    public enum MetricType {
        cpu, gc, tps, heapmemory, heappermgen, heapmetaspace, heapnewgen,
        heapoldgen, heapsurvivor, nonheapmemory, nonheappermgen, nonheapmetaspace,
        nonheapnewgen, nonheapoldgen, nonheapsurvivor
    }
}
