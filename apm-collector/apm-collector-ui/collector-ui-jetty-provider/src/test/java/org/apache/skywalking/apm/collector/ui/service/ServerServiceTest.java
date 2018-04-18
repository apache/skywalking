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
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.apache.skywalking.apm.collector.cache.service.ApplicationCacheService;
import org.apache.skywalking.apm.collector.cache.service.InstanceCacheService;
import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.dao.ui.*;
import org.apache.skywalking.apm.collector.storage.table.register.Application;
import org.apache.skywalking.apm.collector.storage.table.register.Instance;
import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.ResponseTimeTrend;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.common.ThroughputTrend;
import org.apache.skywalking.apm.collector.storage.ui.server.AppServerInfo;
import org.apache.skywalking.apm.collector.storage.ui.server.CPUTrend;
import org.apache.skywalking.apm.collector.storage.ui.server.GCTrend;
import org.apache.skywalking.apm.collector.storage.ui.server.MemoryTrend;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
public class ServerServiceTest {

    private IInstanceUIDAO instanceUIDAO;
    private IInstanceMetricUIDAO instanceMetricUIDAO;
    private ICpuMetricUIDAO cpuMetricUIDAO;
    private IGCMetricUIDAO gcMetricUIDAO;
    private IMemoryMetricUIDAO memoryMetricUIDAO;
    private ApplicationCacheService applicationCacheService;
    private InstanceCacheService instanceCacheService;
    private SecondBetweenService secondBetweenService;
    private ServerService serverService;
    private Duration duration;
    private long startSecondTimeBucket;
    private long endSecondTimeBucket;
    private long startTimeBucket;
    private long endTimeBucket;


    @Before
    public void setUp() throws Exception {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        serverService = new ServerService(moduleManager);
        instanceUIDAO = mock(IInstanceUIDAO.class);
        instanceMetricUIDAO = mock(IInstanceMetricUIDAO.class);
        cpuMetricUIDAO = mock(ICpuMetricUIDAO.class);
        gcMetricUIDAO = mock(IGCMetricUIDAO.class);
        memoryMetricUIDAO = mock(IMemoryMetricUIDAO.class);
        applicationCacheService = mock(ApplicationCacheService.class);
        instanceCacheService = mock(InstanceCacheService.class);
        secondBetweenService = mock(SecondBetweenService.class);
        Whitebox.setInternalState(serverService, "instanceUIDAO", instanceUIDAO);
        Whitebox.setInternalState(serverService, "instanceMetricUIDAO", instanceMetricUIDAO);
        Whitebox.setInternalState(serverService, "cpuMetricUIDAO", cpuMetricUIDAO);
        Whitebox.setInternalState(serverService, "gcMetricUIDAO", gcMetricUIDAO);
        Whitebox.setInternalState(serverService, "memoryMetricUIDAO", memoryMetricUIDAO);
        Whitebox.setInternalState(serverService, "applicationCacheService", applicationCacheService);
        Whitebox.setInternalState(serverService, "instanceCacheService", instanceCacheService);
        Whitebox.setInternalState(serverService, "secondBetweenService", secondBetweenService);
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
    public void searchServer() throws ParseException {
        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());
        when(instanceUIDAO.searchServer(anyString(), anyLong(), anyLong())).then(invocation -> buildServerInfo());
        mockCache();
        List<AppServerInfo> serverInfos = serverService.searchServer("keyword", startSecondTimeBucket, endSecondTimeBucket);
        Assert.assertNotNull(serverInfos.get(0).getPid());
    }

    private List<AppServerInfo> buildServerInfo() {
        AppServerInfo appServerInfo = new AppServerInfo();
        appServerInfo.setId(-1);
        JsonObject jsonObject = new JsonObject();
        jsonObject.addProperty("osName", "Mac");
        jsonObject.addProperty("hostName", "computer");
        jsonObject.addProperty("processId", "1");
        JsonArray jsonElements = new JsonArray();
        jsonElements.add("127.0.0.1");
        jsonObject.add("ipv4s", jsonElements);
        appServerInfo.setOsInfo(new Gson().toJson(jsonObject));
        ArrayList<AppServerInfo> appServerInfos = new ArrayList<>();
        appServerInfos.add(appServerInfo);
        return appServerInfos;
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
    public void getAllServer() throws ParseException {
        when(instanceUIDAO.getAllServer(anyInt(), anyLong(), anyLong())).then(invocation -> buildServerInfo());
        mockCache();
        List<AppServerInfo> allServer = serverService.getAllServer(-1, startSecondTimeBucket, endSecondTimeBucket);
        Assert.assertNotNull(allServer.get(0).getPid());
    }

    @Test
    public void getServerResponseTimeTrend() throws ParseException {
        when(instanceMetricUIDAO.getResponseTimeTrend(anyInt(), anyObject(), anyObject())).then(invocation -> Collections.singletonList(1));
        ResponseTimeTrend serverResponseTimeTrend = serverService.getServerResponseTimeTrend(1, duration.getStep(), startTimeBucket, endTimeBucket);
        Assert.assertTrue(serverResponseTimeTrend.getTrendList().size() == 1);
    }

    @Test
    public void getServerThroughput() throws ParseException {
        when(instanceMetricUIDAO.getServerThroughput(anyInt(), anyObject(), anyLong(), anyLong(), anyInt(), anyInt(), anyObject())).then(invocation -> buildServerInfo());
        when(instanceUIDAO.getInstance(anyInt())).then(invocation -> {
            Instance instance = new Instance();
            JsonObject jsonObject = new JsonObject();
            JsonArray jsonElements = new JsonArray();
            jsonElements.add("127.0.0.1");
            jsonObject.add("ipv4s", jsonElements);
            instance.setOsInfo(new Gson().toJson(jsonObject));
            return instance;
        });
        mockCache();
        List<AppServerInfo> serverThroughput = serverService.getServerThroughput(1, Step.MONTH, startTimeBucket, endTimeBucket, startSecondTimeBucket, endSecondTimeBucket, 10);
        Assert.assertTrue(serverThroughput.size() == 1);
    }

    @Test
    public void getServerTPSTrend() throws ParseException {
        ThroughputTrend serverTPSTrend = serverService.getServerTPSTrend(1, duration.getStep(), startTimeBucket, endTimeBucket);
        Assert.assertNotNull(serverTPSTrend);
    }

    @Test
    public void getCPUTrend() throws ParseException {
        CPUTrend cpuTrend = serverService.getCPUTrend(1, duration.getStep(), startTimeBucket, endTimeBucket);
        Assert.assertNotNull(cpuTrend);
    }

    @Test
    public void getGCTrend() throws ParseException {
        GCTrend gcTrend = serverService.getGCTrend(1, duration.getStep(), startTimeBucket, endTimeBucket);
        Assert.assertNotNull(gcTrend);
    }

    @Test
    public void getMemoryTrend() throws ParseException {
        when(memoryMetricUIDAO.getHeapMemoryTrend(anyInt(), anyObject(), anyObject())).then(invocation -> {
            IMemoryMetricUIDAO.Trend trend = new IMemoryMetricUIDAO.Trend();
            trend.setMaxMetrics(Collections.singletonList(1));
            trend.setMetrics(Collections.singletonList(2));
            return trend;
        });
        when(memoryMetricUIDAO.getNoHeapMemoryTrend(anyInt(), anyObject(), anyObject())).then(invocation -> {
            IMemoryMetricUIDAO.Trend trend = new IMemoryMetricUIDAO.Trend();
            trend.setMaxMetrics(Collections.singletonList(1));
            trend.setMetrics(Collections.singletonList(2));
            return trend;
        });
        MemoryTrend memoryTrend = serverService.getMemoryTrend(1, duration.getStep(), startTimeBucket, endTimeBucket);
        Assert.assertNotNull(memoryTrend);
    }
}