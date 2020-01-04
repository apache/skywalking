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

package org.apache.skywalking.apm.agent.core.profile;

import org.apache.skywalking.apm.agent.core.boot.BootService;
import org.apache.skywalking.apm.agent.core.boot.DefaultNamedThreadFactory;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Profile task executor, use {@link #addProfileTask(ProfileTask)} to add a new profile task.
 *
 * @author MrPro
 */
public class ProfileTaskExecutionService implements BootService {

    private static volatile ScheduledExecutorService PROFILE_TASK_READY_SCHEDULE = Executors.newScheduledThreadPool(15, new DefaultNamedThreadFactory("PROFILE-TASK-READY-SCHEDULE"));

    // last command create time, use to next query task list
    private volatile long lastCommandCreateTime = -1;

    /**
     * get profile task from OAP
     * @param task
     */
    public void addProfileTask(ProfileTask task) {
        // update last command create time
        if (task.getStartTime() > lastCommandCreateTime) {
            lastCommandCreateTime = task.getStartTime();
        }

        long timeFromStartMills = task.getStartTime() - System.currentTimeMillis();
        if (timeFromStartMills <= 0) {
            // task already can start
            processProfileTask(task);
        } else {
            // need to be a schedule to start task
            PROFILE_TASK_READY_SCHEDULE.schedule(new Runnable() {
                @Override
                public void run() {
                    processProfileTask(task);
                }
            }, timeFromStartMills, TimeUnit.MILLISECONDS);
        }
    }

    private void processProfileTask(ProfileTask task) {
        // TODO process task on next step
    }

    @Override
    public void prepare() throws Throwable {

    }

    @Override
    public void boot() throws Throwable {

    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {
        PROFILE_TASK_READY_SCHEDULE.shutdown();
    }

    public long getLastCommandCreateTime() {
        return lastCommandCreateTime;
    }
}
