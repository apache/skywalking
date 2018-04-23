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
import org.junit.Assert;
import org.junit.Test;
import org.powermock.reflect.Whitebox;

/**
 * @author peng-yongsheng
 */
public class DataTTLKeeperTimerTestCase {

    @Test
    public void testConvertTimeBucket() {
        DataTTLKeeperTimer timer = new DataTTLKeeperTimer(null, null, null, 8);
        DataTTLKeeperTimer.TimeBuckets timeBuckets = timer.convertTimeBucket();

        long minuteTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(System.currentTimeMillis());
        long dayTimeBucket = TimeBucketUtils.INSTANCE.minuteToDay(minuteTimeBucket);

        long startSecondTimeBucket = Whitebox.getInternalState(timeBuckets, "startSecondTimeBucket");
        Assert.assertEquals((dayTimeBucket - 8) * 1000000, startSecondTimeBucket);

        long endSecondTimeBucket = Whitebox.getInternalState(timeBuckets, "endSecondTimeBucket");
        Assert.assertEquals(((dayTimeBucket - 8) * 1000000 + 235959), endSecondTimeBucket);

        long startMinuteTimeBucket = Whitebox.getInternalState(timeBuckets, "startMinuteTimeBucket");
        Assert.assertEquals((dayTimeBucket - 8) * 10000, startMinuteTimeBucket);

        long endMinuteTimeBucket = Whitebox.getInternalState(timeBuckets, "endMinuteTimeBucket");
        Assert.assertEquals(((dayTimeBucket - 8) * 10000 + 2359), endMinuteTimeBucket);

        long startHourTimeBucket = Whitebox.getInternalState(timeBuckets, "startHourTimeBucket");
        Assert.assertEquals((dayTimeBucket - 8) * 100, startHourTimeBucket);

        long endHourTimeBucket = Whitebox.getInternalState(timeBuckets, "endHourTimeBucket");
        Assert.assertEquals(((dayTimeBucket - 8) * 100 + 23), endHourTimeBucket);

        long startDayTimeBucket = Whitebox.getInternalState(timeBuckets, "startDayTimeBucket");
        Assert.assertEquals(dayTimeBucket - 8, startDayTimeBucket);

        long endDayTimeBucket = Whitebox.getInternalState(timeBuckets, "endDayTimeBucket");
        Assert.assertEquals(dayTimeBucket - 8, endDayTimeBucket);

        long startMonthTimeBucket = Whitebox.getInternalState(timeBuckets, "startMonthTimeBucket");
        Assert.assertEquals((dayTimeBucket - 8) / 100, startMonthTimeBucket);

        long endMonthTimeBucket = Whitebox.getInternalState(timeBuckets, "endMonthTimeBucket");
        Assert.assertEquals((dayTimeBucket - 8) / 100, endMonthTimeBucket);
    }
}
