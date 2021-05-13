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

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Calendar;
import java.util.TimeZone;

@RunWith(Parameterized.class)
public class TimeBucketTest {
    private static final long NOW = System.currentTimeMillis();

    @Parameterized.Parameters
    public static Object[] parameters() {
        return new Object[]{
                DownSampling.Second,
                DownSampling.Minute,
                DownSampling.Hour,
                DownSampling.Day
        };
    }

    private DownSampling downSampling;

    public TimeBucketTest(DownSampling downSampling) {
        this.downSampling = downSampling;
    }

    @Test
    public void testConversion() {
        long timestamp = TimeBucket.getTimestamp(TimeBucket.getTimeBucket(NOW, downSampling));

        Calendar instance = Calendar.getInstance(TimeZone.getDefault());
        instance.setTimeInMillis(NOW);
        switch (downSampling) {
            case Day: {
                instance.set(Calendar.HOUR_OF_DAY, 0);
                // Fall through
            }
            case Hour: {
                instance.set(Calendar.MINUTE, 0);
                // Fall through
            }
            case Minute: {
                instance.set(Calendar.SECOND, 0);
                // Fall through
            }
            case Second: {
                instance.set(Calendar.MILLISECOND, 0);
                // Fall through
            }
        }
        Assert.assertEquals(instance.getTimeInMillis(), timestamp);
    }
}
