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

package org.apache.skywalking.oap.server.cluster.plugin.kubernetes;

import org.apache.skywalking.oap.server.library.module.ModuleConfig;

/**
 * The configuration of the module of cluster.kubernetes
 *
 * @author gaohongtao
 */
public class ClusterModuleKubernetesConfig extends ModuleConfig {
    private int watchTimeoutSeconds;
    private String namespace;
    private String labelSelector;
    private String uidEnvName;

    public int getWatchTimeoutSeconds() {
        return watchTimeoutSeconds;
    }

    public void setWatchTimeoutSeconds(int watchTimeoutSeconds) {
        this.watchTimeoutSeconds = watchTimeoutSeconds;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public String getLabelSelector() {
        return labelSelector;
    }

    public void setLabelSelector(String labelSelector) {
        this.labelSelector = labelSelector;
    }

    public String getUidEnvName() {
        return uidEnvName;
    }

    public void setUidEnvName(String uidEnvName) {
        this.uidEnvName = uidEnvName;
    }
}
