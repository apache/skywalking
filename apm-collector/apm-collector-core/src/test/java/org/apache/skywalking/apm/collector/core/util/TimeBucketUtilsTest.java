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


package org.apache.skywalking.apm.collector.core.util;

import java.util.TimeZone;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author wu-sheng
 */
public class TimeBucketUtilsTest {
    @Before
    public void setup() {
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Shanghai"));
    }

    @After
    public void teardown() {

    }

    @Test
    public void testGetInfoFromATimestamp() {
        long timeMillis = 1509521745220L;
        Assert.assertArrayEquals(new long[] {
            20171101153545L,
            20171101153544L,
            20171101153543L,
            20171101153542L,
            20171101153541L
        }, TimeBucketUtils.INSTANCE.getFiveSecondTimeBuckets(TimeBucketUtils.INSTANCE.getSecondTimeBucket(timeMillis)));
        Assert.assertEquals(20171101153545L, TimeBucketUtils.INSTANCE.getSecondTimeBucket(timeMillis));
        Assert.assertEquals(201711011535L, TimeBucketUtils.INSTANCE.getMinuteTimeBucket(timeMillis));
        Assert.assertEquals(201711011500L, TimeBucketUtils.INSTANCE.getHourTimeBucket(timeMillis));
        Assert.assertEquals(201711010000L, TimeBucketUtils.INSTANCE.getDayTimeBucket(timeMillis));
    }
}
