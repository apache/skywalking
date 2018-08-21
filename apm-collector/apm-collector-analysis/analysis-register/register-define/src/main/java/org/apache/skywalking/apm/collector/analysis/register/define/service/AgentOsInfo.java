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

package org.apache.skywalking.apm.collector.analysis.register.define.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.List;

/**
 * @author wusheng
 */
public class AgentOsInfo {
    private String osName;
    private String hostname;
    private int processNo;
    private List<String> ipv4s;

    public AgentOsInfo() {
    }

    public String getOsName() {
        return osName;
    }

    public void setOsName(String osName) {
        this.osName = osName;
    }

    public String getHostname() {
        return hostname;
    }

    public void setHostname(String hostname) {
        this.hostname = hostname;
    }

    public int getProcessNo() {
        return processNo;
    }

    public void setProcessNo(int processNo) {
        this.processNo = processNo;
    }

    public List<String> getIpv4s() {
        return ipv4s;
    }

    public void setIpv4s(List<String> ipv4s) {
        this.ipv4s = ipv4s;
    }

    @Override public String toString() {
        JsonObject osInfoJson = new JsonObject();
        osInfoJson.addProperty("osName", this.getOsName());
        osInfoJson.addProperty("hostName", this.getHostname());
        osInfoJson.addProperty("processId", this.getProcessNo());

        JsonArray ipv4Array = new JsonArray();
        if (this.getIpv4s() != null) {
            for (String ipv4 : this.getIpv4s()) {
                ipv4Array.add(ipv4);
            }
        }
        osInfoJson.add("ipv4s", ipv4Array);
        return osInfoJson.toString();
    }
}
