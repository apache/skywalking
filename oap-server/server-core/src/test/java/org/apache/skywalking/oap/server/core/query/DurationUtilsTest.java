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
package org.apache.skywalking.oap.server.core.query;

import org.apache.skywalking.oap.server.core.query.entity.Step;
import org.joda.time.DateTime;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

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
        assertEquals(DATE_LONG * 100 * 100 * 100 * 100, utils.startTimeDurationToSecondTimeBucket(Step.MONTH, DATE_STR));
        assertEquals(DATE_LONG * 100 * 100 * 100, utils.startTimeDurationToSecondTimeBucket(Step.DAY, DATE_STR));
        assertEquals(DATE_LONG * 100 * 100, utils.startTimeDurationToSecondTimeBucket(Step.HOUR, DATE_STR));
        assertEquals(DATE_LONG * 100, utils.startTimeDurationToSecondTimeBucket(Step.MINUTE, DATE_STR));
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

    @Test
    public void minutesBetween() throws Exception {
        DateTime dateTime = new DateTime(2018, 5, 17, 21, 34);
        assertEquals(44640, utils.minutesBetween(Step.MONTH, dateTime));
        assertEquals(1440, utils.minutesBetween(Step.DAY, dateTime));
        assertEquals(60, utils.minutesBetween(Step.HOUR, dateTime));
        assertEquals(1, utils.minutesBetween(Step.MINUTE, dateTime));
        assertEquals(1, utils.minutesBetween(Step.SECOND, dateTime));

        assertEquals(12, utils.minutesBetween(Step.MINUTE, 201805172112L, 201805172124L));
    }

    @Test
    public void secondsBetween() throws Exception {

        DateTime dateTime = new DateTime(2019, 5, 18, 23, 4);

        assertEquals(2678400, utils.secondsBetween(Step.MONTH, dateTime));
        assertEquals(86400, utils.secondsBetween(Step.DAY, dateTime));
        assertEquals(3600, utils.secondsBetween(Step.HOUR, dateTime));
        assertEquals(60, utils.secondsBetween(Step.MINUTE, dateTime));
        assertEquals(1, utils.secondsBetween(Step.SECOND, dateTime));

        assertEquals(10627200, utils.secondsBetween(Step.MONTH, 201805, 201809));

    }

    @Test
    public void getDurationPoints() throws Exception {

        getDurationPoints(Step.MONTH, 201805, 201810);

        getDurationPoints(Step.DAY, 20180503, 20180521);

        getDurationPoints(Step.HOUR, 2018050307, 2018050321);
        getDurationPoints(Step.MINUTE, 201805030743L, 201805030756L);
        getDurationPoints(Step.SECOND, 20180503074312L, 20180503074334L);


    }

    private void getDurationPoints(Step step, long start, long end) throws Exception {
        long size = end - start + 1;
        List<DurationPoint> durations = utils.getDurationPoints(step, start, end);
        assertEquals(size, durations.size());
        for (int i = 0; i < size; i++) {
            DurationPoint point = durations.get(i);
            assertEquals(start + i, point.getPoint());
        }
    }
}