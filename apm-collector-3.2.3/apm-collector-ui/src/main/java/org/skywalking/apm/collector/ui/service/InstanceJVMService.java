/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

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
 * @author peng-yongsheng
 */
public class InstanceJVMService {

    private final Logger logger = LoggerFactory.getLogger(InstanceJVMService.class);

    private Gson gson = new Gson();

    public JsonObject getInstanceOsInfo(int instanceId) {
        IInstanceDAO instanceDAO = (IInstanceDAO)DAOContainer.INSTANCE.get(IInstanceDAO.class.getName());
        InstanceDataDefine.Instance instance = instanceDAO.getInstance(instanceId);
        if (ObjectUtils.isEmpty(instance)) {
            throw new UnexpectedException("instance id: " + instanceId + " not exist.");
        }

        return gson.fromJson(instance.getOsInfo(), JsonObject.class);
    }

    public JsonObject getInstanceJvmMetric(int instanceId, Set<String> metricTypes, long timeBucket) {
        JsonObject metrics = new JsonObject();
        for (String metricType : metricTypes) {
            if (metricType.toLowerCase().equals(MetricType.cpu.name())) {
                ICpuMetricDAO cpuMetricDAO = (ICpuMetricDAO)DAOContainer.INSTANCE.get(ICpuMetricDAO.class.getName());
                metrics.addProperty(MetricType.cpu.name(), cpuMetricDAO.getMetric(instanceId, timeBucket));
            } else if (metricType.toLowerCase().equals(MetricType.gc.name())) {
                IGCMetricDAO gcMetricDAO = (IGCMetricDAO)DAOContainer.INSTANCE.get(IGCMetricDAO.class.getName());
                metrics.add(MetricType.gc.name(), gcMetricDAO.getMetric(instanceId, timeBucket));
            } else if (metricType.toLowerCase().equals(MetricType.tps.name())) {
                IInstPerformanceDAO instPerformanceDAO = (IInstPerformanceDAO)DAOContainer.INSTANCE.get(IInstPerformanceDAO.class.getName());
                metrics.addProperty(MetricType.tps.name(), instPerformanceDAO.getTpsMetric(instanceId, timeBucket));
            } else if (metricType.toLowerCase().equals(MetricType.resptime.name())) {
                IInstPerformanceDAO instPerformanceDAO = (IInstPerformanceDAO)DAOContainer.INSTANCE.get(IInstPerformanceDAO.class.getName());
                metrics.addProperty(MetricType.resptime.name(), instPerformanceDAO.getRespTimeMetric(instanceId, timeBucket));
            } else if (metricType.toLowerCase().equals(MetricType.heapmemory.name())) {
                IMemoryMetricDAO memoryMetricDAO = (IMemoryMetricDAO)DAOContainer.INSTANCE.get(IMemoryMetricDAO.class.getName());
                metrics.add(MetricType.heapmemory.name(), memoryMetricDAO.getMetric(instanceId, timeBucket, true));
            } else if (metricType.toLowerCase().equals(MetricType.nonheapmemory.name())) {
                IMemoryMetricDAO memoryMetricDAO = (IMemoryMetricDAO)DAOContainer.INSTANCE.get(IMemoryMetricDAO.class.getName());
                metrics.add(MetricType.nonheapmemory.name(), memoryMetricDAO.getMetric(instanceId, timeBucket, false));
            } else if (metricType.toLowerCase().equals(MetricType.permgen.name())) {
                IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
                metrics.add(MetricType.permgen.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, PoolType.PERMGEN_USAGE_VALUE));
            } else if (metricType.toLowerCase().equals(MetricType.metaspace.name())) {
                IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
                metrics.add(MetricType.metaspace.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, PoolType.METASPACE_USAGE_VALUE));
            } else if (metricType.toLowerCase().equals(MetricType.newgen.name())) {
                IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
                metrics.add(MetricType.newgen.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, PoolType.NEWGEN_USAGE_VALUE));
            } else if (metricType.toLowerCase().equals(MetricType.oldgen.name())) {
                IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
                metrics.add(MetricType.oldgen.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, PoolType.OLDGEN_USAGE_VALUE));
            } else if (metricType.toLowerCase().equals(MetricType.survivor.name())) {
                IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
                metrics.add(MetricType.survivor.name(), memoryPoolMetricDAO.getMetric(instanceId, timeBucket, PoolType.SURVIVOR_USAGE_VALUE));
            } else {
                throw new UnexpectedException("unexpected metric type");
            }
        }
        return metrics;
    }

    public JsonObject getInstanceJvmMetrics(int instanceId, Set<String> metricTypes, long startTimeBucket,
        long endTimeBucket) {
        JsonObject metrics = new JsonObject();
        for (String metricType : metricTypes) {
            if (metricType.toLowerCase().equals(MetricType.cpu.name())) {
                ICpuMetricDAO cpuMetricDAO = (ICpuMetricDAO)DAOContainer.INSTANCE.get(ICpuMetricDAO.class.getName());
                metrics.add(MetricType.cpu.name(), cpuMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket));
            } else if (metricType.toLowerCase().equals(MetricType.gc.name())) {
                IGCMetricDAO gcMetricDAO = (IGCMetricDAO)DAOContainer.INSTANCE.get(IGCMetricDAO.class.getName());
                metrics.add(MetricType.gc.name(), gcMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket));
            } else if (metricType.toLowerCase().equals(MetricType.tps.name())) {
                IInstPerformanceDAO instPerformanceDAO = (IInstPerformanceDAO)DAOContainer.INSTANCE.get(IInstPerformanceDAO.class.getName());
                metrics.add(MetricType.tps.name(), instPerformanceDAO.getTpsMetric(instanceId, startTimeBucket, endTimeBucket));
            } else if (metricType.toLowerCase().equals(MetricType.resptime.name())) {
                IInstPerformanceDAO instPerformanceDAO = (IInstPerformanceDAO)DAOContainer.INSTANCE.get(IInstPerformanceDAO.class.getName());
                metrics.add(MetricType.resptime.name(), instPerformanceDAO.getRespTimeMetric(instanceId, startTimeBucket, endTimeBucket));
            } else if (metricType.toLowerCase().equals(MetricType.heapmemory.name())) {
                IMemoryMetricDAO memoryMetricDAO = (IMemoryMetricDAO)DAOContainer.INSTANCE.get(IMemoryMetricDAO.class.getName());
                metrics.add(MetricType.heapmemory.name(), memoryMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, true));
            } else if (metricType.toLowerCase().equals(MetricType.nonheapmemory.name())) {
                IMemoryMetricDAO memoryMetricDAO = (IMemoryMetricDAO)DAOContainer.INSTANCE.get(IMemoryMetricDAO.class.getName());
                metrics.add(MetricType.nonheapmemory.name(), memoryMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, false));
            } else if (metricType.toLowerCase().equals(MetricType.permgen.name())) {
                IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
                metrics.add(MetricType.permgen.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, PoolType.PERMGEN_USAGE_VALUE));
            } else if (metricType.toLowerCase().equals(MetricType.metaspace.name())) {
                IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
                metrics.add(MetricType.metaspace.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, PoolType.METASPACE_USAGE_VALUE));
            } else if (metricType.toLowerCase().equals(MetricType.newgen.name())) {
                IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
                metrics.add(MetricType.newgen.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, PoolType.NEWGEN_USAGE_VALUE));
            } else if (metricType.toLowerCase().equals(MetricType.oldgen.name())) {
                IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
                metrics.add(MetricType.oldgen.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, PoolType.OLDGEN_USAGE_VALUE));
            } else if (metricType.toLowerCase().equals(MetricType.survivor.name())) {
                IMemoryPoolMetricDAO memoryPoolMetricDAO = (IMemoryPoolMetricDAO)DAOContainer.INSTANCE.get(IMemoryPoolMetricDAO.class.getName());
                metrics.add(MetricType.survivor.name(), memoryPoolMetricDAO.getMetric(instanceId, startTimeBucket, endTimeBucket, PoolType.SURVIVOR_USAGE_VALUE));
            } else {
                throw new UnexpectedException("unexpected metric type");
            }
        }

        return metrics;
    }

    public enum MetricType {
        cpu, gc, tps, resptime, heapmemory, nonheapmemory, permgen, metaspace, newgen,
        oldgen, survivor
    }
}
