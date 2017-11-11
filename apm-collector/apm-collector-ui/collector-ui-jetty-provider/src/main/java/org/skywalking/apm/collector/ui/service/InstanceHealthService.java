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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.skywalking.apm.collector.cache.CacheServiceManager;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.dao.IGCMetricUIDAO;
import org.skywalking.apm.collector.storage.dao.IInstPerformanceUIDAO;
import org.skywalking.apm.collector.storage.dao.IInstanceUIDAO;
import org.skywalking.apm.collector.storage.service.DAOService;
import org.skywalking.apm.collector.storage.table.register.Instance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceHealthService {

    private final Logger logger = LoggerFactory.getLogger(InstanceHealthService.class);

    private final DAOService daoService;
    private final CacheServiceManager cacheServiceManager;

    public InstanceHealthService(DAOService daoService, CacheServiceManager cacheServiceManager) {
        this.daoService = daoService;
        this.cacheServiceManager = cacheServiceManager;
    }

    public JsonObject getInstances(long timeBucket, int applicationId) {
        JsonObject response = new JsonObject();

        long[] timeBuckets = TimeBucketUtils.INSTANCE.getFiveSecondTimeBuckets(timeBucket);
        long halfHourBeforeTimeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket, -60 * 30);
        IInstanceUIDAO instanceDAO = (IInstanceUIDAO)daoService.get(IInstanceUIDAO.class);
        List<Instance> instanceList = instanceDAO.getInstances(applicationId, halfHourBeforeTimeBucket);

        JsonArray instances = new JsonArray();
        response.add("instances", instances);

        instanceList.forEach(instance -> {
            response.addProperty("applicationCode", cacheServiceManager.getApplicationCacheService().get(applicationId));
            response.addProperty("applicationId", applicationId);

            IInstPerformanceUIDAO instPerformanceDAO = (IInstPerformanceUIDAO)daoService.get(IInstPerformanceUIDAO.class);
            IInstPerformanceUIDAO.InstPerformance performance = instPerformanceDAO.get(timeBuckets, instance.getInstanceId());

            IGCMetricUIDAO gcMetricDAO = (IGCMetricUIDAO)daoService.get(IGCMetricUIDAO.class);
            JsonObject instanceJson = new JsonObject();
            instanceJson.addProperty("id", instance.getInstanceId());
            if (performance != null) {
                instanceJson.addProperty("tps", performance.getCalls());
            } else {
                instanceJson.addProperty("tps", 0);
            }

            int avg = 0;
            if (performance != null && performance.getCalls() != 0) {
                avg = (int)(performance.getCostTotal() / performance.getCalls());
            }
            instanceJson.addProperty("avg", avg);

            if (avg > 5000) {
                instanceJson.addProperty("healthLevel", 0);
            } else if (avg > 3000 && avg <= 5000) {
                instanceJson.addProperty("healthLevel", 1);
            } else if (avg > 1000 && avg <= 3000) {
                instanceJson.addProperty("healthLevel", 2);
            } else {
                instanceJson.addProperty("healthLevel", 3);
            }

            long heartBeatTime = TimeBucketUtils.INSTANCE.changeTimeBucket2TimeStamp(TimeBucketUtils.TimeBucketType.SECOND.name(), instance.getHeartBeatTime());
            long currentTime = TimeBucketUtils.INSTANCE.changeTimeBucket2TimeStamp(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket);

            if (currentTime - heartBeatTime < 1000 * 60 * 2) {
                instanceJson.addProperty("status", 0);
            } else {
                instanceJson.addProperty("status", 1);
            }

            IGCMetricUIDAO.GCCount gcCount = gcMetricDAO.getGCCount(timeBuckets, instance.getInstanceId());
            instanceJson.addProperty("ygc", gcCount.getYoung());
            instanceJson.addProperty("ogc", gcCount.getOld());

            instances.add(instanceJson);
        });

        return response;
    }
}
