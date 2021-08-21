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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.oap.server.core.query.DurationUtils;
import org.apache.skywalking.oap.server.core.query.enumeration.Step;
import org.joda.time.DateTimeZone;
import org.joda.time.Instant;
import org.joda.time.MutableDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.junit.Assert;
import org.junit.Test;

public class BanyanDBTraceQueryDAOTest {
    private static final DateTimeFormatter YYYY_MM_DD = DateTimeFormat.forPattern("yyyy-MM-dd");
    private static final DateTimeFormatter YYYY_MM_DD_HH = DateTimeFormat.forPattern("yyyy-MM-dd HH");
    private static final DateTimeFormatter YYYY_MM_DD_HHMM = DateTimeFormat.forPattern("yyyy-MM-dd HHmm");
    private static final DateTimeFormatter YYYY_MM_DD_HHMMSS = DateTimeFormat.forPattern("yyyy-MM-dd HHmmss");

    @Test
    public void testStartTimeBucket_stepIsSecond() {
        Instant instant = Instant.now();
        String str = YYYY_MM_DD_HHMMSS.print(instant);
        long startSecondsTB = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.SECOND, str);
        long millis = BanyanDBTraceQueryDAO.parseMillisFromStartSecondTB(startSecondsTB);
        Assert.assertEquals(instant.getMillis() / 1000, millis / 1000);
    }

    @Test
    public void testStartTimeBucket_stepIsMinute() {
        Instant instant = Instant.now();
        String str = YYYY_MM_DD_HHMM.print(instant);
        long startSecondsTB = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.MINUTE, str);
        long millis = BanyanDBTraceQueryDAO.parseMillisFromStartSecondTB(startSecondsTB);
        MutableDateTime dateTime = instant.toMutableDateTime(DateTimeZone.UTC);
        dateTime.setSecondOfMinute(0);
        Assert.assertEquals(dateTime.getMillis() / 1000, millis / 1000);
    }

    @Test
    public void testStartTimeBucket_stepIsHour() {
        Instant instant = Instant.now();
        String str = YYYY_MM_DD_HH.print(instant);
        long startSecondsTB = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.HOUR, str);
        long millis = BanyanDBTraceQueryDAO.parseMillisFromStartSecondTB(startSecondsTB);
        MutableDateTime dateTime = instant.toMutableDateTime(DateTimeZone.UTC);
        dateTime.setSecondOfMinute(0);
        dateTime.setMinuteOfHour(0);
        Assert.assertEquals(dateTime.getMillis() / 1000, millis / 1000);
    }

    @Test
    public void testStartTimeBucket_stepIsDay() {
        Instant instant = Instant.now();
        String str = YYYY_MM_DD.print(instant);
        long startSecondsTB = DurationUtils.INSTANCE.startTimeDurationToSecondTimeBucket(Step.DAY, str);
        long millis = BanyanDBTraceQueryDAO.parseMillisFromStartSecondTB(startSecondsTB);
        MutableDateTime dateTime = instant.toMutableDateTime(DateTimeZone.UTC);
        dateTime.setSecondOfMinute(0);
        dateTime.setMinuteOfHour(0);
        dateTime.setHourOfDay(0);
        Assert.assertEquals(dateTime.getMillis() / 1000, millis / 1000);
    }

    @Test
    public void testEndTimeBucket_stepIsSecond() {
        Instant instant = Instant.now();
        String str = YYYY_MM_DD_HHMMSS.print(instant);
        long endSecondsTB = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.SECOND, str);
        long millis = BanyanDBTraceQueryDAO.parseMillisFromEndSecondTB(endSecondsTB);
        Assert.assertEquals(instant.getMillis() / 1000, millis / 1000);
    }

    @Test
    public void testEndTimeBucket_stepIsMinute() {
        Instant instant = Instant.now();
        String str = YYYY_MM_DD_HHMM.print(instant);
        long startSecondsTB = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.MINUTE, str);
        long millis = BanyanDBTraceQueryDAO.parseMillisFromEndSecondTB(startSecondsTB);
        MutableDateTime dateTime = instant.toMutableDateTime(DateTimeZone.UTC);
        dateTime.setSecondOfMinute(0);
        Assert.assertEquals(dateTime.getMillis() / 1000, millis / 1000);
    }

    @Test
    public void testEndTimeBucket_stepIsHour() {
        Instant instant = Instant.now();
        String str = YYYY_MM_DD_HH.print(instant);
        long startSecondsTB = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.HOUR, str);
        long millis = BanyanDBTraceQueryDAO.parseMillisFromEndSecondTB(startSecondsTB);
        MutableDateTime dateTime = instant.toMutableDateTime(DateTimeZone.UTC);
        dateTime.setSecondOfMinute(0);
        dateTime.setMinuteOfHour(0);
        Assert.assertEquals(dateTime.getMillis() / 1000, millis / 1000);
    }

    @Test
    public void testEndTimeBucket_stepIsDay() {
        Instant instant = Instant.now();
        String str = YYYY_MM_DD.print(instant);
        long startSecondsTB = DurationUtils.INSTANCE.endTimeDurationToSecondTimeBucket(Step.DAY, str);
        long millis = BanyanDBTraceQueryDAO.parseMillisFromEndSecondTB(startSecondsTB);
        MutableDateTime dateTime = instant.toMutableDateTime(DateTimeZone.UTC);
        dateTime.setSecondOfMinute(0);
        dateTime.setMinuteOfHour(0);
        dateTime.setHourOfDay(0);
        Assert.assertEquals(dateTime.getMillis() / 1000, millis / 1000);
    }
}
