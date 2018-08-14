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
import org.apache.skywalking.apm.collector.storage.dao.ui.IGlobalTraceUIDAO;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentDurationUIDAO;
import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.trace.BasicTrace;
import org.apache.skywalking.apm.collector.storage.ui.trace.QueryOrder;
import org.apache.skywalking.apm.collector.storage.ui.trace.TraceBrief;
import org.apache.skywalking.apm.collector.storage.ui.trace.TraceState;
import org.apache.skywalking.apm.collector.ui.utils.DurationUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.internal.util.reflection.Whitebox;

import java.text.ParseException;
import java.util.Collections;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author lican
 */
public class SegmentTopServiceTest {

    private ISegmentDurationUIDAO segmentDurationUIDAO;
    private IGlobalTraceUIDAO globalTraceUIDAO;
    private SegmentTopService segmentTopService;
    private Duration duration;

    @Before
    public void setUp() {
        ModuleManager moduleManager = mock(ModuleManager.class);
        when(moduleManager.find(anyString())).then(invocation -> new MockModule());
        segmentTopService = new SegmentTopService(moduleManager);
        segmentDurationUIDAO = mock(ISegmentDurationUIDAO.class);
        globalTraceUIDAO = mock(IGlobalTraceUIDAO.class);
        Whitebox.setInternalState(segmentTopService, "segmentDurationUIDAO", segmentDurationUIDAO);
        Whitebox.setInternalState(segmentTopService, "globalTraceUIDAO", globalTraceUIDAO);
        duration = new Duration();
        duration.setEnd("2018-02");
        duration.setStart("2018-01");
        duration.setStep(Step.MONTH);
    }

    @Test
    public void loadTop() throws ParseException {
        long startSecondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(duration.getStep(), duration.getStart());
        long endSecondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(duration.getStep(), duration.getEnd());
        when(globalTraceUIDAO.getSegmentIds(anyString())).then(invocation -> Collections.singletonList("segmentIds"));
        when(segmentDurationUIDAO.loadTop(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyInt(), anyInt(), anyInt(),anyObject(),anyObject())).then(invocation -> getTrace());
        when(segmentDurationUIDAO.loadTop(anyLong(), anyLong(), anyLong(), anyLong(), anyString(), anyInt(), anyInt(), anyInt(), anyObject(),anyObject(),anyObject())).then(invocation -> getTrace());
        TraceBrief traceBrief = segmentTopService.loadTop(startSecondTimeBucket, endSecondTimeBucket, 0, 1, "test", null, 1, 10, 0,TraceState.ALL,QueryOrder.BY_START_TIME);
        Assert.assertTrue(traceBrief.getTraces().size() == 1);
        traceBrief = segmentTopService.loadTop(startSecondTimeBucket, endSecondTimeBucket, 0, 1, "test", "traceId", 1, 10, 0,TraceState.ALL,QueryOrder.BY_START_TIME);
        Assert.assertTrue(traceBrief.getTraces().size() == 1);
    }

    private TraceBrief getTrace() {
        TraceBrief traceBrief = new TraceBrief();
        BasicTrace basicTrace = new BasicTrace();
        basicTrace.setDuration(12);
        basicTrace.setError(false);
        basicTrace.getOperationName().add("test");
        basicTrace.setSegmentId("segmentId");
        basicTrace.setStart(System.currentTimeMillis());
        basicTrace.setTraceIds(Collections.singletonList("traceId"));
        traceBrief.setTotal(1);
        traceBrief.setTraces(Collections.singletonList(basicTrace));
        return traceBrief;
    }

}