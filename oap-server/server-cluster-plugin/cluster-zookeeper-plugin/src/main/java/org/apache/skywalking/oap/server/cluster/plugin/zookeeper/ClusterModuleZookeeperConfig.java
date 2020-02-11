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

package org.apache.skywalking.oap.server.cluster.plugin.zookeeper;

import com.google.common.base.Strings;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

class ClusterModuleZookeeperConfig extends ModuleConfig {

    @Setter
    @Getter
    private String nameSpace;
    private String hostPort;
    private int baseSleepTimeMs;
    private int maxRetries;
    @Setter
    @Getter
    private String internalComHost;
    @Setter
    @Getter
    private int internalComPort = -1;

    @Setter
    @Getter
    private boolean enableACL = false;
    @Setter
    @Getter
    private String schema;
    @Setter
    @Getter
    private String expression;

    public String getHostPort() {
        return Strings.isNullOrEmpty(hostPort) ? "localhost:2181" : hostPort;
    }

    public void setHostPort(String hostPort) {
        this.hostPort = hostPort;
    }

    public int getBaseSleepTimeMs() {
        return baseSleepTimeMs > 0 ? baseSleepTimeMs : 1000;
    }

    public void setBaseSleepTimeMs(int baseSleepTimeMs) {
        this.baseSleepTimeMs = baseSleepTimeMs;
    }

    public int getMaxRetries() {
        return maxRetries > 0 ? maxRetries : 3;
    }

    public void setMaxRetries(int maxRetries) {
        this.maxRetries = maxRetries;
    }
}
