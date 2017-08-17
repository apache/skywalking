package org.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.ui.cache.ApplicationCache;
import org.skywalking.apm.collector.ui.dao.GCMetricEsDAO;
import org.skywalking.apm.collector.ui.dao.IGCMetricDAO;
import org.skywalking.apm.collector.ui.dao.IInstPerformanceDAO;
import org.skywalking.apm.collector.ui.dao.IInstanceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class InstanceHealthService {

    private final Logger logger = LoggerFactory.getLogger(InstanceHealthService.class);

    public JsonObject getApplications(long timestamp) {
        IInstanceDAO instanceDAO = (IInstanceDAO)DAOContainer.INSTANCE.get(IInstanceDAO.class.getName());
        List<IInstanceDAO.Application> applications = instanceDAO.getApplications(timestamp);

        JsonObject response = new JsonObject();
        JsonArray applicationArray = new JsonArray();

        response.addProperty("timestamp", timestamp);
        response.add("applicationList", applicationArray);

        applications.forEach(application -> {
            JsonObject applicationJson = new JsonObject();
            String applicationCode = ApplicationCache.get(application.getApplicationId());
            applicationJson.addProperty("applicationId", application.getApplicationId());
            applicationJson.addProperty("applicationCode", applicationCode);
            applicationJson.addProperty("instanceCount", application.getCount());
        });

        return response;
    }

    public JsonObject getInstances(long timestamp, int applicationId) {
        JsonObject response = new JsonObject();

        IInstPerformanceDAO instPerformanceDAO = (IInstPerformanceDAO)DAOContainer.INSTANCE.get(IInstPerformanceDAO.class.getName());
        List<IInstPerformanceDAO.InstPerformance> performances = instPerformanceDAO.getMultiple(timestamp, applicationId);

        response.addProperty("timestamp", timestamp);

        JsonArray instances = new JsonArray();
        response.addProperty("applicationCode", ApplicationCache.get(applicationId));
        response.addProperty("applicationId", applicationId);
        response.add("appInstances", instances);

        GCMetricEsDAO gcMetricEsDAO = (GCMetricEsDAO)DAOContainer.INSTANCE.get(GCMetricEsDAO.class.getName());
        performances.forEach(instance -> {
            JsonObject instanceJson = new JsonObject();
            instanceJson.addProperty("id", instance.getInstanceId());
            instanceJson.addProperty("tps", instance.getCallTimes());

            int avg = (int)((instance.getCostTotal() / instance.getCallTimes()) / 1000);
            instanceJson.addProperty("avg", avg);

            if (avg > 5) {
                instanceJson.addProperty("healthLevel", 0);
            } else if (avg > 3 && avg <= 5) {
                instanceJson.addProperty("healthLevel", 1);
            } else if (avg > 1 && avg <= 3) {
                instanceJson.addProperty("healthLevel", 2);
            } else {
                instanceJson.addProperty("healthLevel", 3);
            }

            instanceJson.addProperty("status", 0);

            IGCMetricDAO.GCCount gcCount = gcMetricEsDAO.getGCCount(timestamp, instance.getInstanceId());
            instanceJson.addProperty("ygc", gcCount.getYoung());
            instanceJson.addProperty("ogc", gcCount.getOld());

            instances.add(instanceJson);
        });
        return response;
    }
}
