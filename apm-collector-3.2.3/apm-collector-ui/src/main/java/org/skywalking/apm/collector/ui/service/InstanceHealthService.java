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
import org.skywalking.apm.collector.cache.ApplicationCache;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.dao.DAOContainer;
import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;
import org.skywalking.apm.collector.ui.dao.IGCMetricDAO;
import org.skywalking.apm.collector.ui.dao.IInstPerformanceDAO;
import org.skywalking.apm.collector.ui.dao.IInstanceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceHealthService {

    private final Logger logger = LoggerFactory.getLogger(InstanceHealthService.class);

    public JsonObject getInstances(long timeBucket, int applicationId) {
        JsonObject response = new JsonObject();

        long[] timeBuckets = TimeBucketUtils.INSTANCE.getFiveSecondTimeBuckets(timeBucket);
        long halfHourBeforeTimeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket, -60 * 30);
        IInstanceDAO instanceDAO = (IInstanceDAO)DAOContainer.INSTANCE.get(IInstanceDAO.class.getName());
        List<InstanceDataDefine.Instance> instanceList = instanceDAO.getInstances(applicationId, halfHourBeforeTimeBucket);

        JsonArray instances = new JsonArray();
        response.add("instances", instances);

        instanceList.forEach(instance -> {
            response.addProperty("applicationCode", ApplicationCache.get(applicationId));
            response.addProperty("applicationId", applicationId);

            IInstPerformanceDAO instPerformanceDAO = (IInstPerformanceDAO)DAOContainer.INSTANCE.get(IInstPerformanceDAO.class.getName());
            IInstPerformanceDAO.InstPerformance performance = instPerformanceDAO.get(timeBuckets, instance.getInstanceId());

            IGCMetricDAO gcMetricDAO = (IGCMetricDAO)DAOContainer.INSTANCE.get(IGCMetricDAO.class.getName());
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

            IGCMetricDAO.GCCount gcCount = gcMetricDAO.getGCCount(timeBuckets, instance.getInstanceId());
            instanceJson.addProperty("ygc", gcCount.getYoung());
            instanceJson.addProperty("ogc", gcCount.getOld());

            instances.add(instanceJson);
        });

        return response;
    }
}
