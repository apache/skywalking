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

    private Float sla;
    private Long callsPerSec;
    private Integer responseTimePerSec;
    private Float apdex;
    private Boolean isAlarm;
    private Integer numOfServer;
    private Integer numOfServerAlarm;
    private Integer numOfServiceAlarm;

    public Float getSla() {
        return sla;
    }

    public void setSla(Float sla) {
        this.sla = sla;
    }

    public Long getCallsPerSec() {
        return callsPerSec;
    }

    public void setCallsPerSec(Long callsPerSec) {
        this.callsPerSec = callsPerSec;
    }

    public Integer getResponseTimePerSec() {
        return responseTimePerSec;
    }

    public void setResponseTimePerSec(Integer responseTimePerSec) {
        this.responseTimePerSec = responseTimePerSec;
    }

    public Float getApdex() {
        return apdex;
    }

    public void setApdex(Float apdex) {
        this.apdex = apdex;
    }

    public Boolean getAlarm() {
        return isAlarm;
    }

    public void setAlarm(Boolean alarm) {
        isAlarm = alarm;
    }

    public Integer getNumOfServer() {
        return numOfServer;
    }

    public void setNumOfServer(Integer numOfServer) {
        this.numOfServer = numOfServer;
    }

    public Integer getNumOfServerAlarm() {
        return numOfServerAlarm;
    }

    public void setNumOfServerAlarm(Integer numOfServerAlarm) {
        this.numOfServerAlarm = numOfServerAlarm;
    }

    public Integer getNumOfServiceAlarm() {
        return numOfServiceAlarm;
    }

    public void setNumOfServiceAlarm(Integer numOfServiceAlarm) {
        this.numOfServiceAlarm = numOfServiceAlarm;
    }
}
