/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package org.apache.skywalking.apm.collector.ui.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.dao.IGCMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceUIDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceHealthService {

    private final Logger logger = LoggerFactory.getLogger(InstanceHealthService.class);

    private final IGCMetricUIDAO gcMetricDAO;
    private final IInstanceUIDAO instanceDAO;
    private final IInstanceMetricUIDAO instanceMetricUIDAO;
    private final ApplicationCacheService applicationCacheService;

    public InstanceHealthService(ModuleManager moduleManager) {
        this.gcMetricDAO = moduleManager.find(StorageModule.NAME).getService(IGCMetricUIDAO.class);
        this.instanceDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceUIDAO.class);
        this.instanceMetricUIDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceMetricUIDAO.class);
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
    }

    public JsonObject getInstances(long timeBucket, int applicationId) {
        JsonObject response = new JsonObject();

        long[] timeBuckets = TimeBucketUtils.INSTANCE.getFiveSecondTimeBuckets(timeBucket);
        long halfHourBeforeTimeBucket = TimeBucketUtils.INSTANCE.addSecondForSecondTimeBucket(TimeBucketUtils.TimeBucketType.SECOND.name(), timeBucket, -60 * 30);
        List<Instance> instanceList = instanceDAO.getInstances(applicationId, halfHourBeforeTimeBucket);

        JsonArray instances = new JsonArray();
        response.add("instances", instances);

        instanceList.forEach(instance -> {
            response.addProperty("applicationCode", applicationCacheService.get(applicationId));
            response.addProperty("applicationId", applicationId);

            IInstanceMetricUIDAO.InstanceMetric performance = instanceMetricUIDAO.get(timeBuckets, instance.getInstanceId());

            JsonObject instanceJson = new JsonObject();
            instanceJson.addProperty("id", instance.getInstanceId());
            if (performance != null) {
                instanceJson.addProperty("tps", performance.getCalls());
            } else {
                instanceJson.addProperty("tps", 0);
            }

            int avg = 0;
            if (performance != null && performance.getCalls() != 0) {
                avg = (int)(performance.getDurationSum() / performance.getCalls());
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
