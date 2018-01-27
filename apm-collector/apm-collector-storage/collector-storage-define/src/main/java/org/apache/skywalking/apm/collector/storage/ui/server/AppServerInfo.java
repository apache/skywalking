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

package org.apache.skywalking.apm.collector.storage.ui.server;

import java.util.List;

/**
 * @author peng-yongsheng
 */
public class AppServerInfo {
    private int id;
    private String osInfo;
    private String name;
    private int tps;
    private String host;
    private int pid;
    private List<String> ipv4;

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getOsInfo() {
        return osInfo;
    }

    public void setOsInfo(String osInfo) {
        this.osInfo = osInfo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getTps() {
        return tps;
    }

    public void setTps(int tps) {
        this.tps = tps;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPid() {
        return pid;
    }

    public void setPid(int pid) {
        this.pid = pid;
    }

    public List<String> getIpv4() {
        return ipv4;
    }

    public void setIpv4(List<String> ipv4) {
        this.ipv4 = ipv4;
    }
}
