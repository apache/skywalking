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

import java.util.Objects;

/**
 * Profile task bean, receive from OAP server
 *
 * @author MrPro
 */
public class ProfileTask {

    // monitor endpoint name
    private String endpointName;

    // task duration (minute)
    private int duration;

    // trace start monitoring time (ms)
    private int minDurationThreshold;

    // thread dump period (ms)
    private int threadDumpPeriod;

    // max number of traces monitor on the sniffer
    private int maxSamplingCount;

    // task start time
    private long startTime;

    // task create time
    private long createTime;

    public String getEndpointName() {
        return endpointName;
    }

    public void setEndpointName(String endpointName) {
        this.endpointName = endpointName;
    }

    public int getDuration() {
        return duration;
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getMinDurationThreshold() {
        return minDurationThreshold;
    }

    public void setMinDurationThreshold(int minDurationThreshold) {
        this.minDurationThreshold = minDurationThreshold;
    }

    public int getThreadDumpPeriod() {
        return threadDumpPeriod;
    }

    public void setThreadDumpPeriod(int threadDumpPeriod) {
        this.threadDumpPeriod = threadDumpPeriod;
    }

    public long getStartTime() {
        return startTime;
    }

    public void setStartTime(long startTime) {
        this.startTime = startTime;
    }

    public int getMaxSamplingCount() {
        return maxSamplingCount;
    }

    public void setMaxSamplingCount(int maxSamplingCount) {
        this.maxSamplingCount = maxSamplingCount;
    }

    public long getCreateTime() {
        return createTime;
    }

    public void setCreateTime(long createTime) {
        this.createTime = createTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProfileTask that = (ProfileTask) o;
        return duration == that.duration &&
                minDurationThreshold == that.minDurationThreshold &&
                threadDumpPeriod == that.threadDumpPeriod &&
                maxSamplingCount == that.maxSamplingCount &&
                startTime == that.startTime &&
                createTime == that.createTime &&
                endpointName.equals(that.endpointName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(endpointName, duration, minDurationThreshold, threadDumpPeriod, maxSamplingCount, startTime, createTime);
    }
}
