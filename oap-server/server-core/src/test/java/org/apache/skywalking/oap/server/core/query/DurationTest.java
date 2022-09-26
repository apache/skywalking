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

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.joda.time.DateTime;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.powermock.api.mockito.PowerMockito;

import static org.powermock.api.mockito.PowerMockito.when;

public class DurationTest {
    private final int recordDataTTL = 3;
    private final int metricsDataTTL = 7;

    @Before
    public void init() {
        ConfigService configService = PowerMockito.mock(ConfigService.class);
        when(configService.getMetricsDataTTL()).thenReturn(metricsDataTTL);
        when(configService.getRecordDataTTL()).thenReturn(recordDataTTL);
        DurationUtils.INSTANCE.setConfigService(configService);
    }

    @Test
    public void testConvertToTimeBucket() {
        Assert.assertEquals(20220908L, DurationUtils.INSTANCE.convertToTimeBucket(Step.DAY, "2022-09-08"));
        Assert.assertEquals(2022090810L, DurationUtils.INSTANCE.convertToTimeBucket(Step.HOUR, "2022-09-08 10"));
        Assert.assertEquals(202209081010L, DurationUtils.INSTANCE.convertToTimeBucket(Step.MINUTE, "2022-09-08 1010"));
        Assert.assertEquals(
            20220908101010L, DurationUtils.INSTANCE.convertToTimeBucket(Step.SECOND, "2022-09-08 101010"));
        try {
            DurationUtils.INSTANCE.convertToTimeBucket(Step.DAY, "2022-09-08 10");
            Assert.fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testStartTimeDurationToSecondTimeBucket() {
        long expectedDay = Long.parseLong(new DateTime().toString("yyyyMMdd")) * 1000000;
        String inputDay = new DateTime().toString("yyyy-MM-dd");
        Assert.assertEquals(
            expectedDay, DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.DAY,
                                                                                        DurationUtils.INSTANCE.trimToStartTimeBucket(
                                                                                            Step.DAY, inputDay,
                                                                                            true)
            ));
        long expectedDayOutTTL = Long.parseLong(new DateTime().plusDays(1 - recordDataTTL).toString("yyyyMMdd")) * 1000000;
        String inputDayOutTTL = new DateTime().plusDays(-recordDataTTL).toString("yyyy-MM-dd");
        Assert.assertEquals(
            expectedDayOutTTL, DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.DAY,
                                                                                    DurationUtils.INSTANCE.trimToStartTimeBucket(
                                                                                        Step.DAY, inputDayOutTTL,
                                                                                        true)
            ));
        long expectedHour = Long.parseLong(new DateTime().toString("yyyyMMddHH")) * 10000;
        String inputHour = new DateTime().toString("yyyy-MM-dd HH");
        Assert.assertEquals(
            expectedHour, DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.HOUR,
                                                                                        DurationUtils.INSTANCE.trimToStartTimeBucket(
                                                                                            Step.HOUR, inputHour,
                                                                                            true)
            ));
        String inputHourOutTTL = new DateTime().plusDays(-recordDataTTL).toString("yyyy-MM-dd HH");
        Assert.assertEquals(
            expectedDayOutTTL, DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.HOUR,
                                                                                     DurationUtils.INSTANCE.trimToStartTimeBucket(
                                                                                         Step.HOUR, inputHourOutTTL,
                                                                                         true)
            ));
        long expectedMin = Long.parseLong(new DateTime().toString("yyyyMMddHHmm")) * 100;
        String inputMin = new DateTime().toString("yyyy-MM-dd HHmm");
        Assert.assertEquals(
            expectedMin,
            DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(
                Step.MINUTE, DurationUtils.INSTANCE.trimToStartTimeBucket(Step.MINUTE, inputMin, true))
        );
        String inputMinOutTTL = new DateTime().plusDays(-recordDataTTL).toString("yyyy-MM-dd HHmm");
        Assert.assertEquals(
            expectedDayOutTTL,
            DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(
                Step.MINUTE, DurationUtils.INSTANCE.trimToStartTimeBucket(Step.MINUTE, inputMinOutTTL, true))
        );
        String inputSec = new DateTime().toString("yyyy-MM-dd HHmmss");
        long expectedSec = Long.parseLong(new DateTime().toString("yyyyMMddHHmmss")) ;
        Assert.assertEquals(
            expectedSec,
            DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(
                Step.SECOND, DurationUtils.INSTANCE.trimToStartTimeBucket(Step.SECOND, inputSec, true))
        );
        String inputSecOutTTL = new DateTime().plusDays(-recordDataTTL).toString("yyyy-MM-dd HHmmss");
        Assert.assertEquals(
            expectedDayOutTTL,
            DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(
                Step.SECOND, DurationUtils.INSTANCE.trimToStartTimeBucket(Step.SECOND, inputSecOutTTL, true))
        );
        try {
            DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(
                Step.HOUR, DurationUtils.INSTANCE.trimToStartTimeBucket(Step.HOUR, "2022-09-08 30", true));
            Assert.fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testEndTimeDurationToSecondTimeBucket() {
        Assert.assertEquals(
            20220908235959L, DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.DAY,
                                                                                      DurationUtils.INSTANCE.trimToEndTimeBucket(
                                                                                          Step.DAY, "2022-09-08")
            ));
        Assert.assertEquals(
            20220908105959L, DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.HOUR,
                                                                                      DurationUtils.INSTANCE.trimToEndTimeBucket(
                                                                                          Step.HOUR, "2022-09-08 10")
            ));
        Assert.assertEquals(
            20220908101059L, DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.MINUTE,
                                                                                      DurationUtils.INSTANCE.trimToEndTimeBucket(
                                                                                          Step.MINUTE,
                                                                                          "2022-09-08 1010")
            ));
        Assert.assertEquals(
            20220908101010L,
            DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(
                Step.SECOND, DurationUtils.INSTANCE.trimToEndTimeBucket(Step.SECOND, "2022-09-08 101010"))
        );
        try {
            DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(
                Step.HOUR, DurationUtils.INSTANCE.trimToEndTimeBucket(Step.HOUR, "2022-09-08 30"));
            Assert.fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(true);
        }
    }

    @Test
    public void testGetDurationPoints() {
        List<PointOfTime> pointOfTimes = DurationUtils.INSTANCE.getDurationPoints(Step.DAY, 20220910, 20220912);
        Assert.assertTrue(Arrays.asList(20220910L, 20220911L, 20220912L)
                                .equals(pointOfTimes.stream().map(PointOfTime::getPoint).collect(Collectors.toList())));
    }
}
