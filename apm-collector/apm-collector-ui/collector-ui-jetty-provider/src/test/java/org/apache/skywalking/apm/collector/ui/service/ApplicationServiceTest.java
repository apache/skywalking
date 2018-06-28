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
import org.apache.skywalking.apm.collector.storage.ui.application.Application;
import org.apache.skywalking.apm.collector.storage.ui.common.*;
import org.apache.skywalking.apm.collector.storage.ui.overview.*;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceMetric;
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
public class ApplicationServiceTest {

    private IInstanceUIDAO instanceDAO;
    private IServiceMetricUIDAO serviceMetricUIDAO;
    private IApplicationMetricUIDAO applicationMetricUIDAO;
    private INetworkAddressUIDAO networkAddressUIDAO;
    private ApplicationCacheService applicationCacheService;
    private ServiceNameCacheService serviceNameCacheService;
    private DateBetweenService dateBetweenService;
    private ApplicationService applicationService;
    private Duration duration;

    @Before
    public void setUp() throws Exception {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        applicationService = new ApplicationService(moduleManager);
        instanceDAO = mock(IInstanceUIDAO.class);
        serviceMetricUIDAO = mock(IServiceMetricUIDAO.class);
        applicationMetricUIDAO = mock(IApplicationMetricUIDAO.class);
        networkAddressUIDAO = mock(INetworkAddressUIDAO.class);
        applicationCacheService = mock(ApplicationCacheService.class);
        serviceNameCacheService = mock(ServiceNameCacheService.class);
        dateBetweenService = mock(DateBetweenService.class);
        Whitebox.setInternalState(applicationService, "instanceDAO", instanceDAO);
        Whitebox.setInternalState(applicationService, "serviceMetricUIDAO", serviceMetricUIDAO);
        Whitebox.setInternalState(applicationService, "applicationMetricUIDAO", applicationMetricUIDAO);
        Whitebox.setInternalState(applicationService, "networkAddressUIDAO", networkAddressUIDAO);
        Whitebox.setInternalState(applicationService, "applicationCacheService", applicationCacheService);
        Whitebox.setInternalState(applicationService, "serviceNameCacheService", serviceNameCacheService);
        Whitebox.setInternalState(applicationService, "dateBetweenService", dateBetweenService);
        duration = new Duration();
        duration.setEnd("2018-02");
        duration.setStart("2018-01");
        duration.setStep(Step.MONTH);
    }

    @Test
    public void getApplications() throws ParseException {
        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());
        when(instanceDAO.getApplications(anyLong(), anyLong())).then(invocation -> {
            List<Application> applications = new ArrayList<>(2);
            for (int i = 0; i < 2; i++) {
                Application application = new Application();
                application.setNumOfServer(i);
                application.setName("test");
                application.setId(i);
                applications.add(application);
            }
            return applications;
        });
        mockCache();
        List<Application> applications = applicationService.getApplications(startSecondTimeBucket, endSecondTimeBucket);
        Assert.assertTrue(applications.size() == 1);
    }

    private void mockCache() {
        Mockito.when(applicationCacheService.getApplicationById(anyInt())).then(invocation -> {
            org.apache.skywalking.apm.collector.storage.table.register.Application application = new org.apache.skywalking.apm.collector.storage.table.register.Application();
            application.setApplicationId(1);
            application.setApplicationCode("test");
            return application;
        });
    }

    @Test
    public void getSlowService() throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());
        when(serviceMetricUIDAO.getSlowService(anyInt(), anyObject(), anyLong(), anyLong(), anyInt(), anyObject())).then(invocation -> {
            ServiceMetric serviceMetric = new ServiceMetric();
            serviceMetric.setCalls(200900);
            serviceMetric.getService().setName("test");
            serviceMetric.setAvgResponseTime(100);
            serviceMetric.getService().setId(1);
            return Collections.singletonList(serviceMetric);
        });
        when(serviceNameCacheService.get(anyInt())).then(invocation -> {
            ServiceName serviceName = new ServiceName();
            serviceName.setServiceName("serviceName");
            return serviceName;
        });
        mockCache();
        when(dateBetweenService.minutesBetween(anyInt(), anyLong(), anyLong())).then(invocation -> 20L);
        List<ServiceMetric> slowService = applicationService.getSlowService(1, duration.getStep(), startTimeBucket, endTimeBucket, startSecondTimeBucket, endSecondTimeBucket, 10);
        Assert.assertTrue(slowService.get(0).getCpm() > 0);
    }

    @Test
    public void getTopNApplicationThroughput() throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());
        when(applicationMetricUIDAO.getTopNApplicationThroughput(anyObject(), anyLong(), anyLong(), anyInt(), anyInt(), anyObject())).then(invocation -> {
            ApplicationThroughput applicationThroughput = new ApplicationThroughput();
            applicationThroughput.setApplicationId(-1);
            return Collections.singletonList(applicationThroughput);
        });
        mockCache();
        List<ApplicationThroughput> topNApplicationThroughput = applicationService.getTopNApplicationThroughput(duration.getStep(), startTimeBucket, endTimeBucket, 10);
        Assert.assertTrue(topNApplicationThroughput.size() > 0);
    }

    @Test
    public void getConjecturalApps() throws ParseException {
        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());
        when(networkAddressUIDAO.getConjecturalApps()).then(invocation -> {
            ConjecturalApp conjecturalApp = new ConjecturalApp();
            conjecturalApp.setId(1);
            return Collections.singletonList(conjecturalApp);
        });
        ConjecturalAppBrief conjecturalApps = applicationService.getConjecturalApps(duration.getStep(), startSecondTimeBucket, endSecondTimeBucket);
        Assert.assertTrue(conjecturalApps.getApps().size() > 0);
    }
}