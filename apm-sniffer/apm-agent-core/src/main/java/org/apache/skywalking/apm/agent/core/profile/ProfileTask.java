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

    // task start time
    private long startTime;

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
}
