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

package org.skywalking.collector.baseline.computing.provider.service;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;
import org.junit.Assert;
import org.junit.Test;
import org.skywalking.apm.collector.baseline.computing.DailyData;

/**
 * @author Zhang, Chen
 */
public class DailyDataTest {

    @Test
    public void constructor() {
        int duration = 10;
        int length = 86400 / duration;
        TestDailyData data1 = new TestDailyData(duration, TimeUnit.SECONDS);
        Assert.assertEquals(duration, data1.getDuration());
        Assert.assertEquals(length, data1.length());

        TestDailyData data2 = new TestDailyData(new int[length]);
        Assert.assertEquals(duration, data2.getDuration());
        Assert.assertEquals(length, data2.length());
    }

    @Test
    public void addData() {
        int duration = 15;
        int length = 86400 / duration;
        TestDailyData data1 = new TestDailyData(duration, TimeUnit.SECONDS);
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        int start = (int)(c.getTimeInMillis() / 1000);
        for (int i = 0; i < length; i++) {
            data1.addData(i, start + i * duration);
        }
        for (int i = 0; i < length; i++) {
            Assert.assertEquals(i, data1.getData()[i]);
        }

    }

    class TestDailyData extends DailyData {

        public TestDailyData(int duration, TimeUnit timeUnit) {
            super(duration, timeUnit);
        }

        public TestDailyData(int[] data) {
            super(data);
        }
    }

}
