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

import org.apache.skywalking.apm.collector.core.module.MockModule;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationComponentUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMappingUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationReferenceMetricUIDAO;
import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.common.Topology;
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
public class ApplicationTopologyServiceTest {

    private ApplicationTopologyService applicationTopologyService;
    private IApplicationComponentUIDAO applicationComponentUIDAO;
    private IApplicationMappingUIDAO applicationMappingUIDAO;
    private IApplicationMetricUIDAO applicationMetricUIDAO;
    private IApplicationReferenceMetricUIDAO applicationReferenceMetricUIDAO;
    private Duration duration;

    @Before
    public void setUp() {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        applicationTopologyService = new ApplicationTopologyService(moduleManager);
        applicationComponentUIDAO = mock(IApplicationComponentUIDAO.class);
        applicationMappingUIDAO = mock(IApplicationMappingUIDAO.class);
        applicationMetricUIDAO = mock(IApplicationMetricUIDAO.class);
        applicationReferenceMetricUIDAO = mock(IApplicationReferenceMetricUIDAO.class);
        Whitebox.setInternalState(applicationTopologyService, "applicationComponentUIDAO", applicationComponentUIDAO);
        Whitebox.setInternalState(applicationTopologyService, "applicationMappingUIDAO", applicationMappingUIDAO);
        Whitebox.setInternalState(applicationTopologyService, "applicationMetricUIDAO", applicationMetricUIDAO);
        Whitebox.setInternalState(applicationTopologyService, "applicationReferenceMetricUIDAO", applicationReferenceMetricUIDAO);
        duration = new Duration();
        duration.setEnd("2018-02");
        duration.setStart("2018-01");
        duration.setStep(Step.MONTH);
    }

    @Test
    public void getApplicationTopology() throws ParseException {
        long startTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getStart());
        long endTimeBucket = DurationUtils.INSTANCE.exchangeToTimeBucket(duration.getEnd());

        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());
        when(applicationComponentUIDAO.load(anyObject(), anyLong(), anyLong())).then(invocation -> {
            List<IApplicationComponentUIDAO.ApplicationComponent> componentList = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                IApplicationComponentUIDAO.ApplicationComponent applicationComponent = new IApplicationComponentUIDAO.ApplicationComponent();
                applicationComponent.setApplicationId(i);
                applicationComponent.setComponentId(i);
                componentList.add(applicationComponent);
            }
            return componentList;
        });
        mockMapping();
        Topology topology = applicationTopologyService.getApplicationTopology(duration.getStep(), 1, startTimeBucket, endTimeBucket, startSecondTimeBucket, endSecondTimeBucket);
        Assert.assertNotNull(topology);
    }

    private void mockMapping() {
        Mockito.when(applicationMappingUIDAO.load(anyObject(), anyLong(), anyLong())).then(invocation -> {
            IApplicationMappingUIDAO.ApplicationMapping applicationMapping = new IApplicationMappingUIDAO.ApplicationMapping();
            applicationMapping.setMappingApplicationId(1);
            applicationMapping.setApplicationId(1);
            return Collections.singletonList(applicationMapping);
        });
    }
}