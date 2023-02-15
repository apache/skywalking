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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.shardingsphere;

import org.apache.skywalking.oap.server.core.config.ConfigService;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.joda.time.DateTime;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class DurationWithinTTLTest {
    private final int recordDataTTL = 3;

    @BeforeEach
    public void init() {
        ConfigService configService = mock(ConfigService.class);
        when(configService.getRecordDataTTL()).thenReturn(recordDataTTL);
        DurationWithinTTL.INSTANCE.setConfigService(configService);
    }

    @Test
    public void testGetTrimmedRecordStartTime() {
        String expectedDay = new DateTime().toString("yyyy-MM-dd");
        String inputDay = new DateTime().toString("yyyy-MM-dd");
        Assertions.assertEquals(expectedDay, DurationWithinTTL.INSTANCE.getTrimmedRecordStartTime(Step.DAY, inputDay));
        String expectedDayOutTTL = new DateTime().plusDays(1 - recordDataTTL).toString("yyyy-MM-dd");
        String inputDayOutTTL = new DateTime().plusDays(-recordDataTTL).toString("yyyy-MM-dd");
        Assertions.assertEquals(
            expectedDayOutTTL, DurationWithinTTL.INSTANCE.getTrimmedRecordStartTime(Step.DAY, inputDayOutTTL));

        String expectedHour = new DateTime().toString("yyyy-MM-dd HH");
        String inputHour = new DateTime().toString("yyyy-MM-dd HH");
        Assertions.assertEquals(expectedHour, DurationWithinTTL.INSTANCE.getTrimmedRecordStartTime(Step.HOUR, inputHour));
        String inputHourOutTTL = new DateTime().plusDays(-recordDataTTL).toString("yyyy-MM-dd HH");
        Assertions.assertEquals(
            expectedDayOutTTL + " 00",
            DurationWithinTTL.INSTANCE.getTrimmedRecordStartTime(Step.HOUR, inputHourOutTTL)
        );

        String expectedMin = new DateTime().toString("yyyy-MM-dd HHmm");
        String inputMin = new DateTime().toString("yyyy-MM-dd HHmm");
        Assertions.assertEquals(expectedMin, DurationWithinTTL.INSTANCE.getTrimmedRecordStartTime(Step.MINUTE, inputMin));
        String inputMinOutTTL = new DateTime().plusDays(-recordDataTTL).toString("yyyy-MM-dd HHmm");
        Assertions.assertEquals(
            expectedDayOutTTL + " 0000",
            DurationWithinTTL.INSTANCE.getTrimmedRecordStartTime(Step.MINUTE, inputMinOutTTL)
        );

        String inputSec = new DateTime().toString("yyyy-MM-dd HHmmss");
        String expectedSec = new DateTime().toString("yyyy-MM-dd HHmmss");
        Assertions.assertEquals(expectedSec, DurationWithinTTL.INSTANCE.getTrimmedRecordStartTime(Step.SECOND, inputSec));
        String inputSecOutTTL = new DateTime().plusDays(-recordDataTTL).toString("yyyy-MM-dd HHmmss");
        Assertions.assertEquals(
            expectedDayOutTTL + " 000000",
            DurationWithinTTL.INSTANCE.getTrimmedRecordStartTime(Step.SECOND, inputSecOutTTL)
        );
        try {
            DurationWithinTTL.INSTANCE.getTrimmedRecordStartTime(Step.HOUR, "2022-09-08 30");
            Assertions.fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(true);
        }
    }

    @Test
    public void testGetTrimmedMetricEndTime() {
        String expectedDay = new DateTime().toString("yyyy-MM-dd");
        String inputDay = new DateTime().toString("yyyy-MM-dd");
        Assertions.assertEquals(expectedDay, DurationWithinTTL.INSTANCE.getTrimmedMetricEndTime(Step.DAY, inputDay));
        String expectedDayOutTTL = new DateTime().toString("yyyy-MM-dd");
        String inputDayOutTTL = new DateTime().plusDays(1).toString("yyyy-MM-dd");
        Assertions.assertEquals(
            expectedDayOutTTL, DurationWithinTTL.INSTANCE.getTrimmedMetricEndTime(Step.DAY, inputDayOutTTL));

        String expectedHour = new DateTime().toString("yyyy-MM-dd HH");
        String inputHour = new DateTime().toString("yyyy-MM-dd HH");
        Assertions.assertEquals(expectedHour, DurationWithinTTL.INSTANCE.getTrimmedMetricEndTime(Step.HOUR, inputHour));
        String inputHourOutTTL = new DateTime().plusDays(1).toString("yyyy-MM-dd HH");
        Assertions.assertEquals(
            expectedDayOutTTL + " 23",
            DurationWithinTTL.INSTANCE.getTrimmedMetricEndTime(Step.HOUR, inputHourOutTTL)
        );

        String expectedMin = new DateTime().toString("yyyy-MM-dd HHmm");
        String inputMin = new DateTime().toString("yyyy-MM-dd HHmm");
        Assertions.assertEquals(expectedMin, DurationWithinTTL.INSTANCE.getTrimmedMetricEndTime(Step.MINUTE, inputMin));
        String inputMinOutTTL = new DateTime().plusDays(1).toString("yyyy-MM-dd HHmm");
        Assertions.assertEquals(
            expectedDayOutTTL + " 2359",
            DurationWithinTTL.INSTANCE.getTrimmedMetricEndTime(Step.MINUTE, inputMinOutTTL)
        );

        String inputSec = new DateTime().toString("yyyy-MM-dd HHmmss");
        String expectedSec = new DateTime().toString("yyyy-MM-dd HHmmss");
        Assertions.assertEquals(expectedSec, DurationWithinTTL.INSTANCE.getTrimmedMetricEndTime(Step.SECOND, inputSec));
        String inputSecOutTTL = new DateTime().plusDays(1).toString("yyyy-MM-dd HHmmss");
        Assertions.assertEquals(
            expectedDayOutTTL + " 235959",
            DurationWithinTTL.INSTANCE.getTrimmedMetricEndTime(Step.SECOND, inputSecOutTTL)
        );
        try {
            DurationWithinTTL.INSTANCE.getTrimmedMetricEndTime(Step.HOUR, "2022-09-08 30");
            Assertions.fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            Assertions.assertTrue(true);
        }
    }
}
