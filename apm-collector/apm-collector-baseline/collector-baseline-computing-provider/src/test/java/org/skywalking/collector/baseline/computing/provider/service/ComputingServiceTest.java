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
        ComputingService service2 = new ComputingServiceImpl(0, 0, 0);
        int weeks = 8;
        int duration = 3600;
        int length = 86400 / duration;
        int zeroIndex = length / 2;
        DataOfSingleDay[] metrics = newData(weeks, duration);
        for (int j = 0; j < weeks; j++) {
            int startTime = getStartFromToday(-1 - j);
            for (int i = 0; i < length; i++) {
                if (i == zeroIndex) {
                    metrics[j].addData(0, startTime + i * duration);
                } else {
                    metrics[j].addData(i + j, startTime + i * duration);
                }
            }
        }
        Baseline line1 = service.compute(metrics);
        Assert.assertNotNull(line1);
        Assert.assertEquals(length, line1.length());

        weeks = 3;
        int midWeek = weeks / 2;
        DataOfSingleDay[] metrics2 = newData(weeks, duration);
        for (int j = 0; j < weeks; j++) {
            int startTime = getStartFromToday(-1 - j);
            for (int i = 0; i < length; i++) {
                if (i == zeroIndex) {
                    metrics2[j].addData(0, startTime + i * duration);
                } else {
                    metrics2[j].addData(i + j, startTime + i * duration);
                }
            }
        }
        Baseline line2 = service2.compute(metrics2);
        Assert.assertNotNull(line2);
        Assert.assertEquals(length, line2.length());
        Assert.assertEquals(line2.getData()[zeroIndex], 0);
        Assert.assertArrayEquals(line2.getData(), metrics2[midWeek].getData());
    }

    @Test
    public void checkArgsOfCons() {
        int discard = 3, extent = 2, slope = 3;
        new ComputingServiceImpl(discard, extent, slope);
        boolean ex1 = false, ex2 = false, ex3 = false, ex4 = false;
        try {
            new ComputingServiceImpl(-1, extent, slope);
        } catch (Exception e) {
            ex1 = true;
        }
        Assert.assertTrue(ex1);
        try {
            new ComputingServiceImpl(discard, -1, slope);
        } catch (Exception e) {
            ex2 = true;
        }
        Assert.assertTrue(ex2);
        try {
            new ComputingServiceImpl(discard, extent, -1);
        } catch (Exception e) {
            ex3 = true;
        }
        Assert.assertTrue(ex3);
        try {
            new ComputingServiceImpl(discard, extent, 51);
        } catch (Exception e) {
            ex4 = true;
        }
        Assert.assertTrue(ex4);
    }

    @Test
    public void checkArgsOfCompute() {
        int discard = 3, extent = 2, slope = 3;
        boolean ex1 = false, ex2 = false;
        ComputingService service = new ComputingServiceImpl(discard, extent, slope);
        DataOfSingleDay[] m = newData(discard * 2, 3600);
        try {
            service.compute(m);
        } catch (Exception e) {
            ex1 = true;
        }
        Assert.assertTrue(ex1);
        DataOfSingleDay[] m2 = newData(discard * 2 + 3, 3600);
        m2[discard] = new TestData(1800, TimeUnit.SECONDS);
        try {
            service.compute(m2);
        } catch (Exception e) {
            ex2 = true;
        }
        Assert.assertTrue(ex2);
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
