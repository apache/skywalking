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

package org.apache.skywalking.oap.server.storage.plugin.jdbc.common;

import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import org.apache.skywalking.oap.server.core.analysis.DownSampling;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class TableHelperTest {
    /**
     * A range shorter than a day that crosses midnight touches two day tables: the last thirty minutes, read a
     * few minutes after midnight, must find today's table and not only yesterday's.
     */
    @Test
    public void aRangeShorterThanADayAcrossMidnightTouchesBothDays() {
        final long start = at(2026, 9, 8, 23, 40);
        final long end = at(2026, 9, 9, 0, 10);
        assertEquals(
            Arrays.asList(day(2026, 9, 8), day(2026, 9, 9)),
            TableHelper.dayTimeBucketsBetween(start, end));
    }

    @Test
    public void aRangeWithinOneDayTouchesThatDayOnce() {
        assertEquals(
            Collections.singletonList(day(2026, 9, 8)),
            TableHelper.dayTimeBucketsBetween(at(2026, 9, 8, 10, 0), at(2026, 9, 8, 11, 0)));
        assertEquals(
            Collections.singletonList(day(2026, 9, 8)),
            TableHelper.dayTimeBucketsBetween(at(2026, 9, 8, 10, 0), at(2026, 9, 8, 10, 0)));
    }

    @Test
    public void aRangeOverSeveralDaysTouchesEveryDayInOrder() {
        assertEquals(
            Arrays.asList(day(2026, 9, 8), day(2026, 9, 9), day(2026, 9, 10)),
            TableHelper.dayTimeBucketsBetween(at(2026, 9, 8, 23, 0), at(2026, 9, 10, 1, 0)));
    }

    @Test
    public void aRangeThatEndsBeforeItStartsTouchesNothing() {
        assertEquals(
            Collections.emptyList(),
            TableHelper.dayTimeBucketsBetween(at(2026, 9, 9, 1, 0), at(2026, 9, 8, 23, 0)));
    }

    private static long at(final int year, final int month, final int day, final int hour, final int minute) {
        final Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, hour, minute, 0);
        return calendar.getTimeInMillis();
    }

    private static long day(final int year, final int month, final int day) {
        return TimeBucket.getTimeBucket(at(year, month, day, 0, 0), DownSampling.Day);
    }
}
