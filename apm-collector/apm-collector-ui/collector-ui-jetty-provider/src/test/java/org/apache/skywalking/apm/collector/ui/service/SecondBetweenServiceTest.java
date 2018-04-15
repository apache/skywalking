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
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceUIDAO;
import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import java.text.ParseException;

import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
public class SecondBetweenServiceTest {

    private SecondBetweenService secondBetweenService;

    private IInstanceUIDAO instanceUIDAO;

    private Duration duration;

    @Before
    public void setUp() throws Exception {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        secondBetweenService = new SecondBetweenService(moduleManager);
        instanceUIDAO = mock(IInstanceUIDAO.class);
        Whitebox.setInternalState(secondBetweenService, "instanceUIDAO", instanceUIDAO);
        duration = new Duration();
        duration.setEnd("2018-02");
        duration.setStart("2018-01");
        duration.setStep(Step.MONTH);
    }

    @Test
    public void calculate() throws ParseException {
        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());
        when(instanceUIDAO.getLatestHeartBeatTime(anyInt())).then(invocation -> endSecondTimeBucket);
        int seconds = secondBetweenService.calculate(1, startSecondTimeBucket, endSecondTimeBucket);
        Assert.assertTrue(seconds > 0);
    }
}