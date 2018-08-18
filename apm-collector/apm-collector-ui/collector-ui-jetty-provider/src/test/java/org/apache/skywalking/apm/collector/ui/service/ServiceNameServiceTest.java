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
import org.apache.skywalking.apm.collector.cache.service.*;
import org.apache.skywalking.apm.collector.core.module.*;
import org.apache.skywalking.apm.collector.storage.dao.ui.*;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.ui.common.*;
import org.apache.skywalking.apm.collector.storage.ui.service.*;
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
public class ServiceNameServiceTest {

    private IServiceNameServiceUIDAO serviceNameServiceUIDAO;
    private IServiceMetricUIDAO serviceMetricUIDAO;
    private ServiceNameCacheService serviceNameCacheService;
    private ApplicationCacheService applicationCacheService;
    private DateBetweenService dateBetweenService;
    private ServiceNameService serverNameService;
    private Duration duration;
    private long startSecondTimeBucket;
    private long endSecondTimeBucket;
    private long startTimeBucket;
    private long endTimeBucket;

    @Before
    public void setUp() throws Exception {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        serverNameService = new ServiceNameService(moduleManager);
        serviceNameCacheService = mock(ServiceNameCacheService.class);
        applicationCacheService = mock(ApplicationCacheService.class);
        serviceMetricUIDAO = mock(IServiceMetricUIDAO.class);
        dateBetweenService = mock(DateBetweenService.class);
        Whitebox.setInternalState(serverNameService, "serviceNameCacheService", serviceNameCacheService);
        Whitebox.setInternalState(serverNameService, "applicationCacheService", applicationCacheService);
        Whitebox.setInternalState(serverNameService, "serviceMetricUIDAO", serviceMetricUIDAO);
        Whitebox.setInternalState(serverNameService, "dateBetweenService", dateBetweenService);
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
    public void getCount() {
        int count = serverNameService.getCount();
        Assert.assertTrue(count == 0);
    }

    @Test
    public void searchService() {
        List<ServiceInfo> serviceInfos = serverNameService.searchService("keyword", 0, 10);
        Assert.assertTrue(serviceInfos.size() == 0);
    }

    @Test
    public void getServiceThroughputTrend() throws ParseException {
        ThroughputTrend serviceTPSTrend = serverNameService.getServiceThroughputTrend(1, duration.getStep(), startTimeBucket, endTimeBucket);
        Assert.assertNotNull(serviceTPSTrend);
    }

    @Test
    public void getServiceResponseTimeTrend() throws ParseException {
        ResponseTimeTrend serviceResponseTimeTrend = serverNameService.getServiceResponseTimeTrend(1, duration.getStep(), startTimeBucket, endTimeBucket);
        Assert.assertNotNull(serviceResponseTimeTrend);
    }

    @Test
    public void getServiceSLATrend() throws ParseException {
        SLATrend serviceSLATrend = serverNameService.getServiceSLATrend(1, duration.getStep(), startTimeBucket, endTimeBucket);
        Assert.assertNotNull(serviceSLATrend);
    }

    @Test
    public void getSlowService() throws ParseException {
        when(serviceMetricUIDAO.getSlowService(anyInt(), anyObject(), anyLong(), anyLong(), anyInt(), anyObject())).then(invocation -> {
            ServiceMetric serviceMetric = new ServiceMetric();
            serviceMetric.setCalls(200901);
            serviceMetric.getService().setName("test");
            serviceMetric.setAvgResponseTime(100);
            serviceMetric.getService().setApplicationId(1);
            serviceMetric.getService().setId(1);
            return Collections.singletonList(serviceMetric);
        });
        when(dateBetweenService.minutesBetween(anyInt(), anyLong(), anyLong())).then(invocation -> 20L);
        mockCache();
        List<ServiceMetric> slowService = serverNameService.getSlowService(duration.getStep(), startTimeBucket, endTimeBucket, startSecondTimeBucket, endSecondTimeBucket, 10);
        Assert.assertTrue(slowService.size() > 0);
    }

    private void mockCache() {
        Mockito.when(serviceNameCacheService.get(anyInt())).then(invocation -> {
            ServiceName serviceName = new ServiceName();
            serviceName.setServiceName("test_name");
            serviceName.setApplicationId(1);
            return serviceName;
        });

        Mockito.when(applicationCacheService.getApplicationById(anyInt())).then(invocation -> {
            org.apache.skywalking.apm.collector.storage.table.register.Application application = new org.apache.skywalking.apm.collector.storage.table.register.Application();
            application.setApplicationId(1);
            application.setApplicationCode("test");
            return application;
        });
    }
}