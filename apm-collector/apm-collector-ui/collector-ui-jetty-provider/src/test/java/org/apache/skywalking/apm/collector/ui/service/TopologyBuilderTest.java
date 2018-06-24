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

import java.text.ParseException;
import java.util.*;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.core.module.*;
import org.apache.skywalking.apm.collector.storage.dao.ui.*;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.apache.skywalking.apm.collector.storage.ui.alarm.*;
import org.apache.skywalking.apm.collector.storage.ui.common.*;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;
import org.junit.*;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
public class TopologyBuilderTest {

    private ApplicationCacheService applicationCacheService;
    private ServerService serverService;
    private DateBetweenService dateBetweenService;
    private AlarmService alarmService;
    private TopologyBuilder topologyBuilder;
    private Duration duration;
    private long startSecondTimeBucket;
    private long endSecondTimeBucket;
    private long startTimeBucket;
    private long endTimeBucket;

    @Before
    public void setUp() throws Exception {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        topologyBuilder = new TopologyBuilder(moduleManager);
        applicationCacheService = mock(ApplicationCacheService.class);
        alarmService = mock(AlarmService.class);
        dateBetweenService = mock(DateBetweenService.class);
        Whitebox.setInternalState(topologyBuilder, "applicationCacheService", applicationCacheService);
        Whitebox.setInternalState(topologyBuilder, "dateBetweenService", dateBetweenService);
        duration = new Duration();
        duration.setEnd("2018-02");
        duration.setStart("2018-01");
        duration.setStep(Step.MONTH);
        startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());
        startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());
    }

    @Test
    public void build() throws ParseException {
        List<IApplicationComponentUIDAO.ApplicationComponent> applicationComponents = new ArrayList<>();
        IApplicationComponentUIDAO.ApplicationComponent component = new IApplicationComponentUIDAO.ApplicationComponent();
        component.setComponentId(1);
        component.setApplicationId(2);
        applicationComponents.add(component);

        List<IApplicationMappingUIDAO.ApplicationMapping> applicationMappings = new ArrayList<>();
        IApplicationMappingUIDAO.ApplicationMapping mapping = new IApplicationMappingUIDAO.ApplicationMapping();
        mapping.setApplicationId(2);
        mapping.setMappingApplicationId(3);
        applicationMappings.add(mapping);

        List<IApplicationMetricUIDAO.ApplicationMetric> applicationMetrics = new ArrayList<>();
        IApplicationMetricUIDAO.ApplicationMetric applicationMetric = new IApplicationMetricUIDAO.ApplicationMetric();
        applicationMetric.setCalls(200);
        applicationMetric.setErrorCalls(2);
        applicationMetric.setDurations(100);
        applicationMetric.setSatisfiedCount(100);
        applicationMetric.setToleratingCount(50);
        applicationMetric.setFrustratedCount(50);
        applicationMetric.setErrorDurations(1000);
        applicationMetrics.add(applicationMetric);

        List<IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric> callerReferenceMetric = new ArrayList<>();
        IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric applicationReferenceMetric = new IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric();
        applicationReferenceMetric.setCalls(200);
        applicationReferenceMetric.setErrorCalls(2);
        applicationReferenceMetric.setSource(1);
        applicationReferenceMetric.setTarget(2);
        callerReferenceMetric.add(applicationReferenceMetric);

        List<IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric> calleeReferenceMetric = new ArrayList<>();
        IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric metric = new IApplicationReferenceMetricUIDAO.ApplicationReferenceMetric();
        metric.setCalls(200);
        metric.setErrorCalls(2);
        metric.setSource(1);
        metric.setTarget(2);
        calleeReferenceMetric.add(metric);
        mockCache();

        when(alarmService.loadApplicationAlarmList(anyString(), anyInt(), anyObject(), anyLong(), anyLong(), anyInt(), anyInt())).then(invocation -> {
            Alarm alarm = new Alarm();
            alarm.setItems(Collections.singletonList(new AlarmItem()));
            return alarm;
        });
        when(alarmService.loadInstanceAlarmList(anyString(), anyObject(), anyLong(), anyLong(), anyInt(), anyInt())).then(invocation -> {
            Alarm alarm = new Alarm();
            alarm.setItems(Collections.singletonList(new AlarmItem()));
            return alarm;
        });
        when(alarmService.loadServiceAlarmList(anyString(), anyObject(), anyLong(), anyLong(), anyInt(), anyInt())).then(invocation -> {
            Alarm alarm = new Alarm();
            alarm.setItems(Collections.singletonList(new AlarmItem()));
            return alarm;
        });
        when(dateBetweenService.minutesBetween(anyInt(), anyLong(), anyLong())).then(invocation -> 20L);
        Topology topology = topologyBuilder.build(applicationComponents, applicationMappings, applicationMetrics, callerReferenceMetric, calleeReferenceMetric, startSecondTimeBucket, endSecondTimeBucket);
        Assert.assertNotNull(topology);
    }

    private void mockCache() {
        Mockito.when(applicationCacheService.getApplicationById(anyInt())).then(invocation -> {
            Application application = new Application();
            application.setApplicationId(1);
            application.setApplicationCode("test");
            return application;
        });
    }
}