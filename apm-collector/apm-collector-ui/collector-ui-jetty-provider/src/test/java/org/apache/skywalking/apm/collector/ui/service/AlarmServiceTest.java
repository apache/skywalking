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
 */

package org.apache.skywalking.apm.collector.ui.service;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.dao.ui.*;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.ui.alarm.Alarm;
import org.apache.skywalking.apm.collector.storage.ui.alarm.AlarmItem;
import org.apache.skywalking.apm.collector.storage.ui.alarm.AlarmType;
import org.apache.skywalking.apm.collector.storage.ui.alarm.CauseType;
import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.overview.AlarmTrend;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import java.text.ParseException;
import java.util.Collections;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
public class AlarmServiceTest {

    private AlarmService alarmService;

    private IInstanceUIDAO instanceDAO;
    private IApplicationAlarmUIDAO applicationAlarmUIDAO;
    private IApplicationMappingUIDAO applicationMappingUIDAO;
    private IInstanceAlarmUIDAO instanceAlarmUIDAO;
    private IServiceAlarmUIDAO serviceAlarmUIDAO;
    private IApplicationAlarmListUIDAO applicationAlarmListUIDAO;
    private ApplicationCacheService applicationCacheService;
    private ServiceNameCacheService serviceNameCacheService;
    private Duration duration;

    @Before
    public void setUp() {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        alarmService = new AlarmService(moduleManager);
        instanceDAO = mock(IInstanceUIDAO.class);
        applicationAlarmUIDAO = mock(IApplicationAlarmUIDAO.class);
        applicationMappingUIDAO = mock(IApplicationMappingUIDAO.class);
        instanceAlarmUIDAO = mock(IInstanceAlarmUIDAO.class);
        serviceAlarmUIDAO = mock(IServiceAlarmUIDAO.class);
        applicationAlarmListUIDAO = mock(IApplicationAlarmListUIDAO.class);
        applicationCacheService = mock(ApplicationCacheService.class);
        serviceNameCacheService = mock(ServiceNameCacheService.class);
        Whitebox.setInternalState(alarmService, "instanceDAO", instanceDAO);
        Whitebox.setInternalState(alarmService, "applicationAlarmUIDAO", applicationAlarmUIDAO);
        Whitebox.setInternalState(alarmService, "applicationMappingUIDAO", applicationMappingUIDAO);
        Whitebox.setInternalState(alarmService, "instanceAlarmUIDAO", instanceAlarmUIDAO);
        Whitebox.setInternalState(alarmService, "serviceAlarmUIDAO", serviceAlarmUIDAO);
        Whitebox.setInternalState(alarmService, "applicationAlarmListUIDAO", applicationAlarmListUIDAO);
        Whitebox.setInternalState(alarmService, "applicationCacheService", applicationCacheService);
        Whitebox.setInternalState(alarmService, "serviceNameCacheService", serviceNameCacheService);
        duration = new Duration();
        duration.setEnd("2018-02");
        duration.setStart("2018-01");
        duration.setStep(Step.MONTH);
    }

    @Test
    public void loadApplicationAlarmList() throws ParseException {
        Mockito.when(applicationAlarmUIDAO.loadAlarmList(anyString(), anyLong(), anyLong(), anyInt(), anyInt())).then(invocation -> {
            return testAlarm();
        });
        long startTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart()) / 100;
        long endTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd()) / 100;
        Mockito.when(applicationMappingUIDAO.load(anyObject(), anyLong(), anyLong())).then(invocation -> {
            IApplicationMappingUIDAO.ApplicationMapping applicationMapping = new IApplicationMappingUIDAO.ApplicationMapping();
            applicationMapping.setMappingApplicationId(1);
            applicationMapping.setApplicationId(1);
            return Collections.singletonList(applicationMapping);
        });
        mockCache();
        Alarm alarm = alarmService.loadApplicationAlarmList("keyword", Step.MONTH, startTimeBucket, endTimeBucket, 10, 0);
        Assert.assertTrue(alarm.getItems().size() == 1);
        Assert.assertNotNull(alarm.getItems().get(0).getTitle());
    }

    @Test
    public void loadInstanceAlarmList() throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart()) / 100;
        long endTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd()) / 100;
        Mockito.when(instanceAlarmUIDAO.loadAlarmList(anyString(), anyLong(), anyLong(), anyInt(), anyInt())).then(invocation -> testAlarm());
        mockCache();
        when(instanceDAO.getInstance(anyInt())).then(invocation -> {
            Instance instance = new Instance();
            JsonObject jsonObject = new JsonObject();
            Gson gson = new Gson();
            jsonObject.addProperty("hostName", "testHost");
            instance.setOsInfo(gson.toJson(jsonObject));
            return instance;
        });
        Alarm alarm = alarmService.loadInstanceAlarmList("keyword", Step.MONTH, startTimeBucket, endTimeBucket, 10, 0);
        Assert.assertNotNull(alarm.getItems().get(0).getTitle());
    }

    private Alarm testAlarm() {
        Alarm alarm = new Alarm();
        AlarmItem alarmItem = new AlarmItem();
        alarmItem.setId(1);
        alarmItem.setTitle("test");
        alarmItem.setCauseType(CauseType.SLOW_RESPONSE);
        alarmItem.setAlarmType(AlarmType.APPLICATION);
        alarmItem.setStartTime("2018-01-02 00:00:00");
        alarm.setItems(Collections.singletonList(alarmItem));
        alarm.setTotal(100);
        return alarm;
    }

    @Test
    public void loadServiceAlarmList() throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart()) / 100;
        long endTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd()) / 100;
        Mockito.when(serviceAlarmUIDAO.loadAlarmList(anyString(), anyLong(), anyLong(), anyInt(), anyInt())).then(invocation -> testAlarm());
        mockCache();
        when(serviceNameCacheService.get(anyInt())).then(invocation -> {
            ServiceName serviceName = new ServiceName();
            serviceName.setServiceName("serviceName");
            return serviceName;
        });
        alarmService.loadServiceAlarmList("keyword", Step.MONTH, startTimeBucket, endTimeBucket, 10, 0);
    }

    private void mockCache() {
        Mockito.when(applicationCacheService.getApplicationById(anyInt())).then(invocation -> {
            Application application = new Application();
            application.setApplicationId(1);
            application.setApplicationCode("test");
            return application;
        });
    }

    @Test
    public void getApplicationAlarmTrend() throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());
        when(instanceDAO.getApplications(anyLong(), anyLong())).then(invocation -> {
            org.apache.skywalking.apm.collector.storage.ui.application.Application application = new org.apache.skywalking.apm.collector.storage.ui.application.Application();
            application.setId(1);
            application.setName("test");
            application.setNumOfServer(1);
            return Collections.singletonList(application);
        });
        when(applicationAlarmListUIDAO.getAlarmedApplicationNum(anyObject(), anyLong(), anyLong())).then(invocation -> {
            IApplicationAlarmListUIDAO.AlarmTrend alarmTrend = new IApplicationAlarmListUIDAO.AlarmTrend();
            alarmTrend.setNumberOfApplication(1);
            alarmTrend.setTimeBucket(20170108L);
            return Collections.singletonList(alarmTrend);
        });
        AlarmTrend applicationAlarmTrend = alarmService.getApplicationAlarmTrend(duration.getStep(), startTimeBucket, endTimeBucket, startSecondTimeBucket, endSecondTimeBucket);
        Assert.assertTrue(applicationAlarmTrend.getNumOfAlarmRate().size() > 0);
    }
}