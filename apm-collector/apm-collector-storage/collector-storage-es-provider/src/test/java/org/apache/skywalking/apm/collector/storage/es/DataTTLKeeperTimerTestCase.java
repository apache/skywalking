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

import org.joda.time.DateTime;
import org.junit.*;
import org.powermock.reflect.Whitebox;

/**
 * @author peng-yongsheng
 */
public class DataTTLKeeperTimerTestCase {

    @Test
    public void testConvertTimeBucket() {
        DataTTLKeeperTimer timer = new DataTTLKeeperTimer(null, null, null);

        DateTime currentTime = new DateTime(2018, 5, 26, 15, 5);
        DataTTLKeeperTimer.TimeBuckets timeBuckets = timer.convertTimeBucket(currentTime);

        long startMinuteTimeBucket = Whitebox.getInternalState(timeBuckets, "minuteTimeBucketBefore");
        Assert.assertEquals(201805261420L, startMinuteTimeBucket);

        long startHourTimeBucket = Whitebox.getInternalState(timeBuckets, "hourTimeBucketBefore");
        Assert.assertEquals(2018052503, startHourTimeBucket);

        long startDayTimeBucket = Whitebox.getInternalState(timeBuckets, "dayTimeBucketBefore");
        Assert.assertEquals(20180411, startDayTimeBucket);

        long startMonthTimeBucket = Whitebox.getInternalState(timeBuckets, "monthTimeBucketBefore");
        Assert.assertEquals(201611, startMonthTimeBucket);
    }
}
