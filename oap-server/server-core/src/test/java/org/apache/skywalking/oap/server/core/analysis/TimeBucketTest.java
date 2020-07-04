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

package org.apache.skywalking.oap.server.core.analysis;

import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static java.util.concurrent.TimeUnit.DAYS;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

@RunWith(Parameterized.class)
public class TimeBucketTest {
    private static final long NOW = System.currentTimeMillis();

    @Parameterized.Parameters
    public static Object[][] parameters() {
        return new Object[][] {
            {
                DownSampling.Second,
                SECONDS,
                MILLISECONDS.toSeconds(NOW)
            },
            {
                DownSampling.Minute,
                MINUTES,
                MILLISECONDS.toMinutes(NOW)
            },
            {
                DownSampling.Hour,
                HOURS,
                MILLISECONDS.toHours(NOW)
            },
            {
                DownSampling.Day,
                DAYS,
                MILLISECONDS.toDays(NOW)
            },
            };
    }

    private DownSampling downSampling;
    private TimeUnit unit;
    private long time;

    public TimeBucketTest(DownSampling downSampling, TimeUnit unit, long time) {
        this.downSampling = downSampling;
        this.unit = unit;
        this.time = time;
    }

    @Test
    public void testConversion() {
        long timestamp = TimeBucket
            .getTimestamp(TimeBucket.getTimeBucket(NOW, downSampling));
        Assert.assertEquals(timestamp, unit.toMillis(time));
    }

}
