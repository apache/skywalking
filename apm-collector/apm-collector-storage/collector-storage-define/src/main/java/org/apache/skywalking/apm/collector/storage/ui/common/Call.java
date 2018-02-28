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

package org.apache.skywalking.apm.collector.storage.ui.common;

/**
 * @author peng-yongsheng
 */
public class Call {
    private int source;
    private String sourceName;
    private int target;
    private String targetName;
    private boolean isAlert;
    private String callType;
    private long callsPerSec;
    private long avgResponseTime;

    public int getSource() {
        return source;
    }

    public void setSource(int source) {
        this.source = source;
    }

    public String getSourceName() {
        return sourceName;
    }

    public void setSourceName(String sourceName) {
        this.sourceName = sourceName;
    }

    public int getTarget() {
        return target;
    }

    public void setTarget(int target) {
        this.target = target;
    }

    public String getTargetName() {
        return targetName;
    }

    public void setTargetName(String targetName) {
        this.targetName = targetName;
    }

    public boolean isAlert() {
        return isAlert;
    }

    public void setAlert(boolean alert) {
        isAlert = alert;
    }

    public String getCallType() {
        return callType;
    }

    public void setCallType(String callType) {
        this.callType = callType;
    }

    public long getCallsPerSec() {
        return callsPerSec;
    }

    public void setCallsPerSec(long callsPerSec) {
        this.callsPerSec = callsPerSec;
    }

    public long getAvgResponseTime() {
        return avgResponseTime;
    }

    public void setAvgResponseTime(long avgResponseTime) {
        this.avgResponseTime = avgResponseTime;
    }
}
