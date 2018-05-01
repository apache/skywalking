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

package org.apache.skywalking.apm.collector.storage.es;

import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.junit.*;
import org.powermock.reflect.Whitebox;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

/**
 * @author peng-yongsheng
 */
public class DataTTLKeeperTimerTestCase {

    @Test
    public void testConvertTimeBucket() throws ParseException {
        DataTTLKeeperTimer timer = new DataTTLKeeperTimer(null, null, null, 8);
        DataTTLKeeperTimer.TimeBuckets timeBuckets = timer.convertTimeBucket();

        long minuteTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(System.currentTimeMillis());
        long dayTimeBucket = TimeBucketUtils.INSTANCE.minuteToDay(minuteTimeBucket);

        Date dayTimeBucketSource = new SimpleDateFormat("yyyyMMdd").parse(String.valueOf(dayTimeBucket));

        Calendar calendar = Calendar.getInstance();
        calendar.setTime(dayTimeBucketSource);
        calendar.add(Calendar.DAY_OF_MONTH, -8);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);

        long newMinuteTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(calendar.getTimeInMillis());
        long newDayTimeBucket = TimeBucketUtils.INSTANCE.minuteToDay(newMinuteTimeBucket);

        long startSecondTimeBucket = Whitebox.getInternalState(timeBuckets, "startSecondTimeBucket");
        Assert.assertEquals(newDayTimeBucket * 1000000, startSecondTimeBucket);

        long endSecondTimeBucket = Whitebox.getInternalState(timeBuckets, "endSecondTimeBucket");
        Assert.assertEquals(newDayTimeBucket * 1000000 + 235959, endSecondTimeBucket);

        long startMinuteTimeBucket = Whitebox.getInternalState(timeBuckets, "startMinuteTimeBucket");
        Assert.assertEquals(newDayTimeBucket * 10000, startMinuteTimeBucket);

        long endMinuteTimeBucket = Whitebox.getInternalState(timeBuckets, "endMinuteTimeBucket");
        Assert.assertEquals(newDayTimeBucket * 10000 + 2359, endMinuteTimeBucket);

        long startHourTimeBucket = Whitebox.getInternalState(timeBuckets, "startHourTimeBucket");
        Assert.assertEquals(newDayTimeBucket * 100, startHourTimeBucket);

        long endHourTimeBucket = Whitebox.getInternalState(timeBuckets, "endHourTimeBucket");
        Assert.assertEquals(newDayTimeBucket * 100 + 23, endHourTimeBucket);

        long startDayTimeBucket = Whitebox.getInternalState(timeBuckets, "startDayTimeBucket");
        Assert.assertEquals(newDayTimeBucket, startDayTimeBucket);

        long endDayTimeBucket = Whitebox.getInternalState(timeBuckets, "endDayTimeBucket");
        Assert.assertEquals(newDayTimeBucket, endDayTimeBucket);

        long startMonthTimeBucket = Whitebox.getInternalState(timeBuckets, "startMonthTimeBucket");
        Assert.assertEquals(newDayTimeBucket / 100, startMonthTimeBucket);

        long endMonthTimeBucket = Whitebox.getInternalState(timeBuckets, "endMonthTimeBucket");
        Assert.assertEquals(newDayTimeBucket / 100, endMonthTimeBucket);
    }
}
