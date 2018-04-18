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
package org.apache.skywalking.apm.collector.ui.query;

import org.apache.commons.lang3.StringUtils;
import org.apache.skywalking.apm.collector.storage.ui.common.Duration;
import org.apache.skywalking.apm.collector.storage.ui.common.Pagination;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.ui.trace.TraceQueryCondition;
import org.apache.skywalking.apm.collector.ui.service.SegmentTopService;
import org.apache.skywalking.apm.collector.ui.service.TraceStackService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.internal.util.reflection.Whitebox;

import java.text.ParseException;

/**
 * @author lican
 */
public class TraceQueryTest {

    private SegmentTopService segmentTopService;
    private TraceStackService traceStackService;
    private TraceQuery traceQuery;

    @Before
    public void setUp() throws Exception {
        traceQuery = new TraceQuery(null);
        segmentTopService = Mockito.mock(SegmentTopService.class);
        traceStackService = Mockito.mock(TraceStackService.class);
        Whitebox.setInternalState(traceQuery, "segmentTopService", segmentTopService);
        Whitebox.setInternalState(traceQuery, "traceStackService", traceStackService);
    }

    @Test
    public void queryBasicTraces() throws ParseException {

        TraceQueryCondition traceQueryCondition = new TraceQueryCondition();
        traceQueryCondition.setApplicationId(-1);
        traceQueryCondition.setOperationName("operationName");
        Duration duration = new Duration();
        duration.setStart("2017-01");
        duration.setEnd("2017-02");
        duration.setStep(Step.MONTH);
        traceQueryCondition.setQueryDuration(duration);
        traceQueryCondition.setPaging(new Pagination());
        Mockito.when(segmentTopService.loadTop(
                Mockito.anyLong(), Mockito.anyLong(),
                Mockito.anyLong(), Mockito.anyLong(),
                Mockito.anyString(), Mockito.anyString(),
                Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt()
                )
        ).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(arguments[5], StringUtils.trimToEmpty(traceQueryCondition.getTraceId()));
            if (StringUtils.isBlank(traceQueryCondition.getTraceId())) {
                Assert.assertEquals(20170100000000L, arguments[0]);
                Assert.assertEquals(20170299999999L, arguments[1]);
            } else {
                Assert.assertEquals(0L, arguments[0]);
                Assert.assertEquals(0L, arguments[1]);
            }
            return null;
        });
        traceQuery.queryBasicTraces(traceQueryCondition);
        traceQueryCondition.setTraceId("12.12");
        traceQuery.queryBasicTraces(traceQueryCondition);
    }

    @Test
    public void queryTrace() {
        String traceId = "12.12";
        Mockito.when(traceStackService.load(Mockito.anyString())
        ).then(invocation -> {
            Object[] arguments = invocation.getArguments();
            Assert.assertEquals(arguments[0], traceId);
            return null;
        });
        traceQuery.queryTrace(traceId);
    }
}