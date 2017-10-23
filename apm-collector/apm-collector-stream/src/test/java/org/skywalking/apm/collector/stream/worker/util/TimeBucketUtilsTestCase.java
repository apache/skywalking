/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.stream.worker.util;

import java.util.Calendar;
import java.util.TimeZone;
import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;

/**
 * @author peng-yongsheng
 */
public class TimeBucketUtilsTestCase {

    @Test
    public void testUTCLocation() {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        long timeBucket = 201703310915L;
        long changedTimeBucket = TimeBucketUtils.INSTANCE.changeToUTCTimeBucket(timeBucket);
        Assert.assertEquals(201703310115L, changedTimeBucket);
    }

    @Test
    public void testUTC8Location() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+08:00"));
        long timeBucket = 201703310915L;
        long changedTimeBucket = TimeBucketUtils.INSTANCE.changeToUTCTimeBucket(timeBucket);
        Assert.assertEquals(201703310915L, changedTimeBucket);
    }

    @Test
    public void testGetSecondTimeBucket() {
        TimeZone.setDefault(TimeZone.getTimeZone("GMT+08:00"));
        long timeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(1490922929258L);
        Assert.assertEquals(20170331091529L, timeBucket);
    }

    @Test
    public void test() {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(1490922929258L);
        calendar.set(Calendar.SECOND, calendar.get(Calendar.SECOND) - 3);
        calendar.set(Calendar.SECOND, calendar.get(Calendar.SECOND) - 2);
        calendar.set(Calendar.SECOND, calendar.get(Calendar.SECOND) - 2);
    }
}
