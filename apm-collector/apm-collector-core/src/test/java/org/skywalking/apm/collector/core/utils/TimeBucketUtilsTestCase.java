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

package org.skywalking.apm.collector.core.utils;

import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;

/**
 * @author peng-yongsheng
 */
public class TimeBucketUtilsTestCase {

    @Test
    public void testGetFiveSecondTimeBucket() {
        long[] timeBuckets = TimeBucketUtils.INSTANCE.getFiveSecondTimeBuckets(20170804224810L);
        Assert.assertEquals(20170804224810L, timeBuckets[0]);
        Assert.assertEquals(20170804224809L, timeBuckets[1]);
        Assert.assertEquals(20170804224808L, timeBuckets[2]);
        Assert.assertEquals(20170804224807L, timeBuckets[3]);
        Assert.assertEquals(20170804224806L, timeBuckets[4]);
    }

    @Test
    public void testChangeTimeBucket2TimeStamp() {
        long timeStamp = TimeBucketUtils.INSTANCE.changeTimeBucket2TimeStamp(TimeBucketUtils.TimeBucketType.MINUTE.name(), 201708120810L);
        long minute = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(timeStamp);
        Assert.assertEquals(201708120810L, minute);
    }
}
