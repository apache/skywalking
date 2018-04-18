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

package org.apache.skywalking.apm.collector.ui.utils;

import org.apache.skywalking.apm.collector.core.UnexpectedException;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.DurationPoint;
import org.junit.Assert;
import org.junit.Test;

import java.text.ParseException;
import java.util.List;

/**
 * @author peng-yongsheng
 */
public class DurationUtilsTestCase {

    @Test
    public void test() throws ParseException {
    }

    @Test
    public void testGetDurationPoints() throws ParseException {
        List<DurationPoint> durationPoints = DurationUtils.INSTANCE.getDurationPoints(Step.MONTH, 201710, 201803);
        Assert.assertEquals(6, durationPoints.size());
        Assert.assertEquals(201710L, durationPoints.get(0).getPoint());
        Assert.assertEquals(2678400, durationPoints.get(0).getSecondsBetween());
        Assert.assertEquals(201711L, durationPoints.get(1).getPoint());
        Assert.assertEquals(2592000, durationPoints.get(1).getSecondsBetween());
        Assert.assertEquals(201712L, durationPoints.get(2).getPoint());
        Assert.assertEquals(2678400, durationPoints.get(2).getSecondsBetween());
        Assert.assertEquals(201801L, durationPoints.get(3).getPoint());
        Assert.assertEquals(2678400, durationPoints.get(3).getSecondsBetween());
        Assert.assertEquals(201802L, durationPoints.get(4).getPoint());
        Assert.assertEquals(2419200, durationPoints.get(4).getSecondsBetween());
        Assert.assertEquals(201803L, durationPoints.get(5).getPoint());
        Assert.assertEquals(2678400, durationPoints.get(5).getSecondsBetween());

        durationPoints = DurationUtils.INSTANCE.getDurationPoints(Step.DAY, 20180129, 20180202);
        Assert.assertEquals(5, durationPoints.size());
        Assert.assertEquals(20180129L, durationPoints.get(0).getPoint());
        Assert.assertEquals(86400, durationPoints.get(0).getSecondsBetween());
        Assert.assertEquals(20180130L, durationPoints.get(1).getPoint());
        Assert.assertEquals(86400, durationPoints.get(1).getSecondsBetween());
        Assert.assertEquals(20180131L, durationPoints.get(2).getPoint());
        Assert.assertEquals(86400, durationPoints.get(2).getSecondsBetween());
        Assert.assertEquals(20180201L, durationPoints.get(3).getPoint());
        Assert.assertEquals(86400, durationPoints.get(3).getSecondsBetween());
        Assert.assertEquals(20180202L, durationPoints.get(4).getPoint());
        Assert.assertEquals(86400, durationPoints.get(4).getSecondsBetween());

        durationPoints = DurationUtils.INSTANCE.getDurationPoints(Step.HOUR, 2018012922, 2018013002);
        Assert.assertEquals(5, durationPoints.size());
        Assert.assertEquals(2018012922L, durationPoints.get(0).getPoint());
        Assert.assertEquals(3600, durationPoints.get(0).getSecondsBetween());
        Assert.assertEquals(2018012923L, durationPoints.get(1).getPoint());
        Assert.assertEquals(3600, durationPoints.get(1).getSecondsBetween());
        Assert.assertEquals(2018013000L, durationPoints.get(2).getPoint());
        Assert.assertEquals(3600, durationPoints.get(2).getSecondsBetween());
        Assert.assertEquals(2018013001L, durationPoints.get(3).getPoint());
        Assert.assertEquals(3600, durationPoints.get(3).getSecondsBetween());
        Assert.assertEquals(2018013002L, durationPoints.get(4).getPoint());
        Assert.assertEquals(3600, durationPoints.get(4).getSecondsBetween());

        durationPoints = DurationUtils.INSTANCE.getDurationPoints(Step.MINUTE, 201801292258L, 201801292302L);
        Assert.assertEquals(5, durationPoints.size());
        Assert.assertEquals(201801292258L, durationPoints.get(0).getPoint());
        Assert.assertEquals(60, durationPoints.get(0).getSecondsBetween());
        Assert.assertEquals(201801292259L, durationPoints.get(1).getPoint());
        Assert.assertEquals(60, durationPoints.get(1).getSecondsBetween());
        Assert.assertEquals(201801292300L, durationPoints.get(2).getPoint());
        Assert.assertEquals(60, durationPoints.get(2).getSecondsBetween());
        Assert.assertEquals(201801292301L, durationPoints.get(3).getPoint());
        Assert.assertEquals(60, durationPoints.get(3).getSecondsBetween());
        Assert.assertEquals(201801292302L, durationPoints.get(4).getPoint());
        Assert.assertEquals(60, durationPoints.get(4).getSecondsBetween());

        durationPoints = DurationUtils.INSTANCE.getDurationPoints(Step.SECOND, 20180129225858L, 20180129225902L);
        Assert.assertEquals(5, durationPoints.size());
        Assert.assertEquals(20180129225858L, durationPoints.get(0).getPoint());
        Assert.assertEquals(1, durationPoints.get(0).getSecondsBetween());
        Assert.assertEquals(20180129225859L, durationPoints.get(1).getPoint());
        Assert.assertEquals(1, durationPoints.get(1).getSecondsBetween());
        Assert.assertEquals(20180129225900L, durationPoints.get(2).getPoint());
        Assert.assertEquals(1, durationPoints.get(2).getSecondsBetween());
        Assert.assertEquals(20180129225901L, durationPoints.get(3).getPoint());
        Assert.assertEquals(1, durationPoints.get(3).getSecondsBetween());
        Assert.assertEquals(20180129225902L, durationPoints.get(4).getPoint());
        Assert.assertEquals(1, durationPoints.get(4).getSecondsBetween());
    }

    @Test(expected = UnexpectedException.class)
    public void testGetDurationPointsErrorDuration() throws ParseException {
        DurationUtils.INSTANCE.getDurationPoints(Step.MONTH, 20171001, 20180301);
    }

    @Test
    public void testStartTimeDurationToSecondTimeBucket() throws ParseException {
        long secondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.MONTH, "201710");
        Assert.assertEquals(20171000000000L, secondTimeBucket);

        secondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.DAY, "20171001");
        Assert.assertEquals(20171001000000L, secondTimeBucket);

        secondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.HOUR, "2017100108");
        Assert.assertEquals(20171001080000L, secondTimeBucket);

        secondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.MINUTE, "201710010805");
        Assert.assertEquals(20171001080500L, secondTimeBucket);

        secondTimeBucket = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.SECOND, "20171001080501");
        Assert.assertEquals(20171001080501L, secondTimeBucket);
    }

    @Test
    public void testEndTimeDurationToSecondTimeBucket() throws ParseException {
        long secondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.MONTH, "201710");
        Assert.assertEquals(20171099999999L, secondTimeBucket);

        secondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.DAY, "20171001");
        Assert.assertEquals(20171001999999L, secondTimeBucket);

        secondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.HOUR, "2017100108");
        Assert.assertEquals(20171001089999L, secondTimeBucket);

        secondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.MINUTE, "201710010805");
        Assert.assertEquals(20171001080599L, secondTimeBucket);

        secondTimeBucket = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.SECOND, "20171001080501");
        Assert.assertEquals(20171001080501L, secondTimeBucket);
    }

    @Test
    public void testSecondsBetween() throws ParseException {
        int secondsBetweenMonth = DurationUtils.INSTANCE.secondsBetween(Step.MONTH, 201804L, 201805L);
        Assert.assertEquals(secondsBetweenMonth, 30 * 24 * 3600);

        int secondsBetweenDay = DurationUtils.INSTANCE.secondsBetween(Step.DAY, 20180401L, 20180402L);
        Assert.assertEquals(secondsBetweenDay, 24 * 3600);

        int secondsBetweenHour = DurationUtils.INSTANCE.secondsBetween(Step.HOUR, 2018040101L, 2018040102L);
        Assert.assertEquals(secondsBetweenHour, 3600);

        int secondsBetweenMinute = DurationUtils.INSTANCE.secondsBetween(Step.MINUTE, 201804010100L, 201804010101L);
        Assert.assertEquals(secondsBetweenMinute, 60);

        int secondsBetweenSecond = DurationUtils.INSTANCE.secondsBetween(Step.SECOND, 20180401010000L, 20180401010001L);
        Assert.assertEquals(secondsBetweenSecond, 1);

    }
}
