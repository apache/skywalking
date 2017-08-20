package org.skywalking.apm.collector.ui.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.skywalking.apm.collector.core.framework.UnexpectedException;
import org.skywalking.apm.collector.core.util.ObjectUtils;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.table.instance.Instance;
import org.skywalking.apm.collector.ui.dao.IInstanceDAO;
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
        Instance instance = instanceDAO.getInstance(instanceId);
        if (ObjectUtils.isEmpty(instance)) {
            throw new UnexpectedException("instance id: " + instance + " not exist.");
        }

        JsonObject response = gson.fromJson(instance.getOsInfo(), JsonObject.class);
        return response;
    }

    public JsonObject getInstanceJvmMetric(int instanceId, String metricType) {

        return null;
    }

    public enum MetricType {
        cpu, gc, tps, heapmemory, heappermgen, heapmetaspace, heapnewgen,
        heapoldgen, heapsurvivor, nonheap, memory, nonheappermgen, nonheapmetaspace,
        nonheapnewgen, nonheapoldgen, nonheapsurvivor
    }
}
