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

import org.apache.skywalking.apm.agent.core.conf.Config;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * profile task execution context, it will create on process this profile task
 *
 * @author MrPro
 */
public class ProfileTaskExecutionContext {

    // task data
    private final ProfileTask task;

    // task real start time
    private final long startTime;

    // record current profiling count, use this to check has available profile slot
    private final AtomicInteger currentProfilingCount = new AtomicInteger(0);

    // profiling segment slot
    private final ProfilingSegmentContext[] profilingSegmentSlot = new ProfilingSegmentContext[Config.Profile.MAX_PARALLEL];

    // current profile is still running
    private volatile boolean running = true;

    public ProfileTaskExecutionContext(ProfileTask task, long startTime) {
        this.task = task;
        this.startTime = startTime;
    }

    public ProfileTask getTask() {
        return task;
    }

    public long getStartTime() {
        return startTime;
    }

    public AtomicInteger getCurrentProfilingCount() {
        return currentProfilingCount;
    }

    public ProfilingSegmentContext[] getProfilingSegmentSlot() {
        return profilingSegmentSlot;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfileTaskExecutionContext that = (ProfileTaskExecutionContext) o;
        return Objects.equals(task, that.task);
    }

    @Override
    public int hashCode() {
        return Objects.hash(task);
    }

    public boolean getRunning() {
        return running;
    }

    public void setRunning(boolean running) {
        this.running = running;
    }
}
