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
import org.skywalking.apm.collector.baseline.computing.Baseline;
import org.skywalking.apm.collector.baseline.computing.DataOfSingleDay;
import org.skywalking.apm.collector.baseline.computing.service.ComputingService;

/**
 * @author Zhang, Chen
 */
public class ComputingServiceTest {

    @Test
    public void computing() {
        ComputingService service = new ComputingServiceImpl();
        int weeks = 8;
        int duration = 3600;
        int length = 86400 / duration;
        DataOfSingleDay[] metrics = newData(weeks, duration);
        for (int j = 0; j < weeks; j++) {
            int startTime = getStartFromToday(-1 - j);
            for (int i = 0; i < length; i++) {
                metrics[j].addData(i + j, startTime + i * metrics[j].getDuration());
            }
        }
        Baseline list = service.compute(metrics);
        Assert.assertNotNull(list);
        Assert.assertEquals(length, list.length());
    }

    private DataOfSingleDay[] newData(int weeks, int duration) {
        DataOfSingleDay[] metrics = new DataOfSingleDay[weeks];
        for (int j = 0; j < metrics.length; j++) {
            metrics[j] = new TestData(duration, TimeUnit.SECONDS);
        }
        return metrics;
    }

    private int getStartFromToday(int days) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        c.set(Calendar.DATE, days);
        return (int)(c.getTimeInMillis() / 1000);
    }
}
