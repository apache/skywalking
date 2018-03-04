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

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.skywalking.apm.collector.cache.CacheModule;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationAlarmListUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationAlarmUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMappingUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceAlarmUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceAlarmUIDAO;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.ui.alarm.Alarm;
import org.apache.skywalking.apm.collector.storage.ui.application.Application;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.overview.AlarmTrend;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class AlarmService {

    private final Logger logger = LoggerFactory.getLogger(AlarmService.class);

    private final Gson gson = new Gson();
    private final IInstanceUIDAO instanceDAO;
    private final IApplicationAlarmUIDAO applicationAlarmUIDAO;
    private final IApplicationMappingUIDAO applicationMappingUIDAO;
    private final IInstanceAlarmUIDAO instanceAlarmUIDAO;
    private final IServiceAlarmUIDAO serviceAlarmUIDAO;
    private final IApplicationAlarmListUIDAO applicationAlarmListUIDAO;
    private final ApplicationCacheService applicationCacheService;
    private final ServiceNameCacheService serviceNameCacheService;
    private static final String RESPONSE_TIME_ALARM = " response time alarm.";
    private static final String SUCCESS_RATE_ALARM = " success rate alarm.";

    public AlarmService(ModuleManager moduleManager) {
        this.instanceDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceUIDAO.class);
        this.applicationAlarmUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationAlarmUIDAO.class);
        this.applicationMappingUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationMappingUIDAO.class);
        this.instanceAlarmUIDAO = moduleManager.find(StorageModule.NAME).getService(IInstanceAlarmUIDAO.class);
        this.serviceAlarmUIDAO = moduleManager.find(StorageModule.NAME).getService(IServiceAlarmUIDAO.class);
        this.applicationAlarmListUIDAO = moduleManager.find(StorageModule.NAME).getService(IApplicationAlarmListUIDAO.class);
        this.applicationCacheService = moduleManager.find(CacheModule.NAME).getService(ApplicationCacheService.class);
        this.serviceNameCacheService = moduleManager.find(CacheModule.NAME).getService(ServiceNameCacheService.class);
    }

    public Alarm loadApplicationAlarmList(String keyword, Step step, long startTimeBucket, long endTimeBucket,
        int limit, int from) throws ParseException {
        logger.debug("keyword: {}, startTimeBucket: {}, endTimeBucket: {}, limit: {}, from: {}", keyword, startTimeBucket, endTimeBucket, limit, from);
        Alarm alarm = applicationAlarmUIDAO.loadAlarmList(keyword, startTimeBucket, endTimeBucket, limit, from);
        List<IApplicationMappingUIDAO.ApplicationMapping> applicationMappings = applicationMappingUIDAO.load(step, startTimeBucket, endTimeBucket);
        Map<Integer, Integer> mappings = new HashMap<>();
        applicationMappings.forEach(applicationMapping -> mappings.put(applicationMapping.getMappingApplicationId(), applicationMapping.getApplicationId()));

        alarm.getItems().forEach(item -> {
            String applicationCode = applicationCacheService.getApplicationById(mappings.getOrDefault(item.getId(), item.getId())).getApplicationCode();
            switch (item.getCauseType()) {
                case SLOW_RESPONSE:
                    item.setTitle("Application " + applicationCode + RESPONSE_TIME_ALARM);
                    break;
                case LOW_SUCCESS_RATE:
                    item.setTitle("Application " + applicationCode + SUCCESS_RATE_ALARM);
                    break;
            }
        });
        return alarm;
    }

    public Alarm loadInstanceAlarmList(String keyword, Step step, long startTimeBucket, long endTimeBucket,
        int limit, int from) throws ParseException {
        logger.debug("keyword: {}, startTimeBucket: {}, endTimeBucket: {}, limit: {}, from: {}", keyword, startTimeBucket, endTimeBucket, limit, from);
        Alarm alarm = instanceAlarmUIDAO.loadAlarmList(keyword, startTimeBucket, endTimeBucket, limit, from);

        List<IApplicationMappingUIDAO.ApplicationMapping> applicationMappings = applicationMappingUIDAO.load(step, startTimeBucket, endTimeBucket);
        Map<Integer, Integer> mappings = new HashMap<>();
        applicationMappings.forEach(applicationMapping -> mappings.put(applicationMapping.getMappingApplicationId(), applicationMapping.getApplicationId()));

        alarm.getItems().forEach(item -> {
            Instance instance = instanceDAO.getInstance(item.getId());
            String applicationCode = applicationCacheService.getApplicationById(mappings.getOrDefault(instance.getApplicationId(), instance.getApplicationId())).getApplicationCode();
            String serverName = buildServerName(instance.getOsInfo());
            switch (item.getCauseType()) {
                case SLOW_RESPONSE:
                    item.setTitle("Server " + serverName + " of Application " + applicationCode + RESPONSE_TIME_ALARM);
                    break;
                case LOW_SUCCESS_RATE:
                    item.setTitle("Server " + serverName + " of Application  " + applicationCode + SUCCESS_RATE_ALARM);
                    break;
            }
        });

        return alarm;
    }

    public Alarm loadServiceAlarmList(String keyword, Step step, long startTimeBucket, long endTimeBucket,
        int limit, int from) throws ParseException {
        logger.debug("keyword: {}, startTimeBucket: {}, endTimeBucket: {}, limit: {}, from: {}", keyword, startTimeBucket, endTimeBucket, limit, from);
        Alarm alarm = serviceAlarmUIDAO.loadAlarmList(keyword, startTimeBucket, endTimeBucket, limit, from);

        List<IApplicationMappingUIDAO.ApplicationMapping> applicationMappings = applicationMappingUIDAO.load(step, startTimeBucket, endTimeBucket);
        Map<Integer, Integer> mappings = new HashMap<>();
        applicationMappings.forEach(applicationMapping -> mappings.put(applicationMapping.getMappingApplicationId(), applicationMapping.getApplicationId()));

        alarm.getItems().forEach(item -> {
            ServiceName serviceName = serviceNameCacheService.get(item.getId());
            String applicationCode = applicationCacheService.getApplicationById(mappings.getOrDefault(serviceName.getApplicationId(), serviceName.getApplicationId())).getApplicationCode();
            switch (item.getCauseType()) {
                case SLOW_RESPONSE:
                    item.setTitle("Service " + serviceName.getServiceName() + " of Application " + applicationCode + RESPONSE_TIME_ALARM);
                    break;
                case LOW_SUCCESS_RATE:
                    item.setTitle("Service " + serviceName.getServiceName() + " of Application  " + applicationCode + SUCCESS_RATE_ALARM);
                    break;
            }
        });
        return alarm;
    }

    public AlarmTrend getApplicationAlarmTrend(Step step, long startTimeBucket, long endTimeBucket,
        long startSecondTimeBucket,
        long endSecondTimeBucket) throws ParseException {
        List<Application> applications = instanceDAO.getApplications(startSecondTimeBucket, endSecondTimeBucket);

        List<DurationPoint> durationPoints = DurationUtils.INSTANCE.getDurationPoints(step, startTimeBucket, endTimeBucket);

        List<IApplicationAlarmListUIDAO.AlarmTrend> alarmTrends = applicationAlarmListUIDAO.getAlarmedApplicationNum(step, startTimeBucket, endTimeBucket);

        Map<Long, Integer> trendsMap = new HashMap<>();
        alarmTrends.forEach(alarmTrend -> trendsMap.put(alarmTrend.getTimeBucket(), alarmTrend.getNumberOfApplication()));

        AlarmTrend alarmTrend = new AlarmTrend();
        durationPoints.forEach(durationPoint -> {
            if (applications.size() == 0) {
                alarmTrend.getNumOfAlarmRate().add(0);
            } else {
                alarmTrend.getNumOfAlarmRate().add((trendsMap.getOrDefault(durationPoint.getPoint(), 0) * 10000) / (applications.size()));
            }
        });
        return alarmTrend;
    }

    private String buildServerName(String osInfoJson) {
        JsonObject osInfo = gson.fromJson(osInfoJson, JsonObject.class);
        String serverName = Const.UNKNOWN;
        if (osInfo.has("hostName")) {
            serverName = osInfo.get("hostName").getAsString();
        }
        return serverName;
    }
}
