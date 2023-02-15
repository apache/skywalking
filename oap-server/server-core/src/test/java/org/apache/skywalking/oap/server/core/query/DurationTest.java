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

import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class DurationTest {

    @Test
    public void testConvertToTimeBucket() {
        Assertions.assertEquals(20220908L, DurationUtils.INSTANCE.convertToTimeBucket(Step.DAY, "2022-09-08"));
        Assertions.assertEquals(2022090810L, DurationUtils.INSTANCE.convertToTimeBucket(Step.HOUR, "2022-09-08 10"));
        Assertions.assertEquals(202209081010L, DurationUtils.INSTANCE.convertToTimeBucket(Step.MINUTE, "2022-09-08 1010"));
        Assertions.assertEquals(
            20220908101010L, DurationUtils.INSTANCE.convertToTimeBucket(Step.SECOND, "2022-09-08 101010"));
        try {
            DurationUtils.INSTANCE.convertToTimeBucket(Step.DAY, "2022-09-08 10");
            Assertions.fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(true);
        }
    }

    @Test
    public void testStartTimeDurationToSecondTimeBucket() {
        Assertions.assertEquals(
            20220908000000L, DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.DAY, "2022-09-08"));
        Assertions.assertEquals(
            20220908100000L, DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.HOUR, "2022-09-08 10"));
        Assertions.assertEquals(
            20220908101000L,
            DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.MINUTE, "2022-09-08 1010")
        );
        Assertions.assertEquals(
            20220908101010L,
            DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.SECOND, "2022-09-08 101010")
        );
        try {
            DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.HOUR, "2022-09-08 30");
            Assertions.fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(true);
        }
    }

    @Test
    public void testEndTimeDurationToSecondTimeBucket() {
        Assertions.assertEquals(
            20220908235959L, DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.DAY, "2022-09-08"));
        Assertions.assertEquals(
            20220908105959L, DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.HOUR, "2022-09-08 10"));
        Assertions.assertEquals(
            20220908101059L, DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.MINUTE, "2022-09-08 1010"));
        Assertions.assertEquals(
            20220908101010L,
            DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.SECOND, "2022-09-08 101010")
        );
        try {
            DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.HOUR, "2022-09-08 30");
            Assertions.fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(true);
        }
    }

    @Test
    public void testGetDurationPoints() {
        List<PointOfTime> pointOfTimes = DurationUtils.INSTANCE.getDurationPoints(Step.DAY, 20220910, 20220912);
        Assertions.assertTrue(Arrays.asList(20220910L, 20220911L, 20220912L)
                                .equals(pointOfTimes.stream().map(PointOfTime::getPoint).collect(Collectors.toList())));
    }
}
