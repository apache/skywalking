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

import org.apache.skywalking.apm.collector.cache.service.ServiceNameCacheService;
import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationComponentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IServiceReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.table.register.ServiceName;
import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.Node;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.common.Topology;
import org.apache.skywalking.apm.collector.storage.ui.service.ServiceNode;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
public class ServiceTopologyServiceTest {

    private IApplicationComponentUIDAO applicationComponentUIDAO;
    private IServiceMetricUIDAO serviceMetricUIDAO;
    private IServiceReferenceMetricUIDAO serviceReferenceMetricUIDAO;
    private ServiceNameCacheService serviceNameCacheService;
    private SecondBetweenService secondBetweenService;
    private ServiceTopologyService serviceTopologyService;
    private Duration duration;
    private long startSecondTimeBucket;
    private long endSecondTimeBucket;
    private long startTimeBucket;
    private long endTimeBucket;

    @Before
    public void setUp() throws Exception {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        serviceTopologyService = new ServiceTopologyService(moduleManager);
        applicationComponentUIDAO = mock(IApplicationComponentUIDAO.class);
        serviceMetricUIDAO = mock(IServiceMetricUIDAO.class);
        serviceReferenceMetricUIDAO = mock(IServiceReferenceMetricUIDAO.class);
        serviceNameCacheService = mock(ServiceNameCacheService.class);
        secondBetweenService = mock(SecondBetweenService.class);
        Whitebox.setInternalState(serviceTopologyService, "applicationComponentUIDAO", applicationComponentUIDAO);
        Whitebox.setInternalState(serviceTopologyService, "serviceMetricUIDAO", serviceMetricUIDAO);
        Whitebox.setInternalState(serviceTopologyService, "serviceReferenceMetricUIDAO", serviceReferenceMetricUIDAO);
        Whitebox.setInternalState(serviceTopologyService, "serviceNameCacheService", serviceNameCacheService);
        Whitebox.setInternalState(serviceTopologyService, "secondBetweenService", secondBetweenService);
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
    public void getServiceTopology() throws ParseException {
        when(applicationComponentUIDAO.load(anyObject(), anyLong(), anyLong())).then(invocation -> {
            List<IApplicationComponentUIDAO.ApplicationComponent> componentList = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                IApplicationComponentUIDAO.ApplicationComponent applicationComponent = new IApplicationComponentUIDAO.ApplicationComponent();
                applicationComponent.setApplicationId(i + 2);
                applicationComponent.setComponentId(i + 2);
                componentList.add(applicationComponent);
            }
            return componentList;
        });
        when(serviceReferenceMetricUIDAO.getFrontServices(anyObject(), anyLong(), anyLong(), anyObject(), anyInt())).then(invocation -> {
            List<IServiceReferenceMetricUIDAO.ServiceReferenceMetric> list = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                IServiceReferenceMetricUIDAO.ServiceReferenceMetric serviceReferenceMetric = new IServiceReferenceMetricUIDAO.ServiceReferenceMetric();
                serviceReferenceMetric.setSource(i);
                serviceReferenceMetric.setTarget(i + 1);
                serviceReferenceMetric.setCalls(200);
                serviceReferenceMetric.setErrorCalls(2);
                list.add(serviceReferenceMetric);
            }
            return list;
        });
        mockCache();

        when(secondBetweenService.calculate(anyInt(), anyLong(), anyLong())).then(invocation -> 20L);
        when(serviceMetricUIDAO.getServicesMetric(anyObject(), anyLong(), anyLong(), anyObject(), anyObject())).then(invocation -> {
            List<Node> nodes = new LinkedList<>();
            ServiceNode serviceNode = new ServiceNode();
            serviceNode.setId(1);
            serviceNode.setCalls(200);
            serviceNode.setSla(99);
            nodes.add(serviceNode);
            return nodes;
        });
        Topology serviceTopology = serviceTopologyService.getServiceTopology(duration.getStep(), 1, startTimeBucket, endTimeBucket, startSecondTimeBucket, endSecondTimeBucket);
        Assert.assertTrue(serviceTopology.getCalls().size() > 0);
        Assert.assertTrue(serviceTopology.getNodes().size() > 0);
    }

    private void mockCache() {
        Mockito.when(serviceNameCacheService.get(anyInt())).then(invocation -> {
            ServiceName serviceName = new ServiceName();
            serviceName.setServiceName("test_name");
            return serviceName;
        });
    }
}