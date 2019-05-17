package org.apache.skywalking.oap.server.core.query;/*
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

import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Created by dengming in 2019-05-16
 */
public class DurationUtilsTest {

    private DurationUtils utils = DurationUtils.INSTANCE;

    private static final String DATE_STR = "14--  1-2     4- -36   ";

    private static final long DATE_LONG = 1412436L;
    @Before
    public void setUp() throws Exception {
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void exchangeToTimeBucket() {
        assertEquals(DATE_LONG, utils.exchangeToTimeBucket(DATE_STR));
    }

    @Test
    public void startTimeDurationToSecondTimeBucket() {
        assertEquals(DATE_LONG*100*100*100*100, utils.startTimeDurationToSecondTimeBucket(Step.MONTH, DATE_STR));
        assertEquals(DATE_LONG*100*100*100, utils.startTimeDurationToSecondTimeBucket(Step.DAY, DATE_STR));
        assertEquals(DATE_LONG*100*100, utils.startTimeDurationToSecondTimeBucket(Step.HOUR, DATE_STR));
        assertEquals(DATE_LONG*100, utils.startTimeDurationToSecondTimeBucket(Step.MINUTE, DATE_STR));
        assertEquals(DATE_LONG, utils.startTimeDurationToSecondTimeBucket(Step.SECOND, DATE_STR));
    }

    @Test
    public void endTimeDurationToSecondTimeBucket() {
        long start = DATE_LONG;
        assertEquals(start, utils.endTimeDurationToSecondTimeBucket(Step.SECOND, DATE_STR));
        start = start * 100 + 99;
        assertEquals(start, utils.endTimeDurationToSecondTimeBucket(Step.MINUTE, DATE_STR));
        start = start * 100 + 99;
        assertEquals(start, utils.endTimeDurationToSecondTimeBucket(Step.HOUR, DATE_STR));
        start = start * 100 + 99;
        assertEquals(start, utils.endTimeDurationToSecondTimeBucket(Step.DAY, DATE_STR));
        start = start * 100 + 99;
        assertEquals(start, utils.endTimeDurationToSecondTimeBucket(Step.MONTH, DATE_STR));

    }
}