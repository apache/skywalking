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

package org.apache.skywalking.apm.collector.storage.ui.application;

import org.apache.skywalking.apm.collector.storage.ui.common.Node;

/**
 * @author peng-yongsheng
 */
public class ApplicationNode extends Node {

    private int sla;
    private long callsPerSec;
    private long avgResponseTime;
    private int apdex;
    private boolean isAlarm;
    private int numOfServer;
    private int numOfServerAlarm;
    private int numOfServiceAlarm;

    public int getSla() {
        return sla;
    }

    public void setSla(int sla) {
        this.sla = sla;
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

    public int getApdex() {
        return apdex;
    }

    public void setApdex(int apdex) {
        this.apdex = apdex;
    }

    public boolean isAlarm() {
        return isAlarm;
    }

    public void setAlarm(boolean alarm) {
        isAlarm = alarm;
    }

    public int getNumOfServer() {
        return numOfServer;
    }

    public void setNumOfServer(int numOfServer) {
        this.numOfServer = numOfServer;
    }

    public int getNumOfServerAlarm() {
        return numOfServerAlarm;
    }

    public void setNumOfServerAlarm(int numOfServerAlarm) {
        this.numOfServerAlarm = numOfServerAlarm;
    }

    public int getNumOfServiceAlarm() {
        return numOfServiceAlarm;
    }

    public void setNumOfServiceAlarm(int numOfServiceAlarm) {
        this.numOfServiceAlarm = numOfServiceAlarm;
    }
}
