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

package org.apache.skywalking.apm.meter.micrometer;

import io.micrometer.core.instrument.LongTaskTimer;
import org.junit.Assert;
import org.junit.Test;

import java.util.concurrent.TimeUnit;

public class SkywalkingLongTaskTimerTest {

    @Test
    public void testSimple() throws InterruptedException {
        // Creating a simplify long task timer
        final SkywalkingMeterRegistry registry = new SkywalkingMeterRegistry();
        final LongTaskTimer longTaskTimer = registry.more().longTaskTimer("test_simple_long_task_timer", "skywalking", "test");

        // Adding tasks
        addLongTask(longTaskTimer, 450);
        addLongTask(longTaskTimer, 20);

        // Make sure the second task has finished
        TimeUnit.MILLISECONDS.sleep(200);

        // Check Skywalking type
        Assert.assertTrue(longTaskTimer instanceof SkywalkingLongTaskTimer);
        final SkywalkingLongTaskTimer timer = (SkywalkingLongTaskTimer) longTaskTimer;

        // Check Original data
        Assert.assertEquals(1, timer.activeTasks());
        Assert.assertTrue(timer.duration(TimeUnit.MILLISECONDS) > 0);
        Assert.assertTrue(timer.max(TimeUnit.MILLISECONDS) > 0);

    }

    // Add long time task
    private void addLongTask(LongTaskTimer longTaskTimer, int sleepMills) {
        new Thread(() -> {
            longTaskTimer.record(() -> {
                try {
                    TimeUnit.MILLISECONDS.sleep(sleepMills);
                } catch (InterruptedException e) {
                }
            });
        }).start();
    }

}
