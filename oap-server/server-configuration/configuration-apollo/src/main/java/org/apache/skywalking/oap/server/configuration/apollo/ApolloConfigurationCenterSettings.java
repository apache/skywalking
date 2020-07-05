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

package org.apache.skywalking.oap.server.configuration.apollo;

import org.apache.skywalking.oap.server.library.module.ModuleConfig;

public class ApolloConfigurationCenterSettings extends ModuleConfig {
    private String apolloCluster = "default";
    private String apolloMeta;
    private String apolloEnv;
    private String appId = "skywalking";
    private String namespace = "application";
    private String clusterName = "default";
    private int period = 60;

    public String getApolloCluster() {
        return this.apolloCluster;
    }

    public String getClusterName() {
        return this.clusterName;
    }

    public String getApolloMeta() {
        return apolloMeta;
    }

    public void setApolloMeta(String apolloMeta) {
        this.apolloMeta = apolloMeta;
    }

    public String getApolloEnv() {
        return apolloEnv;
    }

    public void setApolloEnv(String apolloEnv) {
        this.apolloEnv = apolloEnv;
    }

    public String getAppId() {
        return appId;
    }

    public void setAppId(String appId) {
        this.appId = appId;
    }

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public int getPeriod() {
        return this.period;
    }

    public void setApolloCluster(String apolloCluster) {
        this.apolloCluster = apolloCluster;
    }

    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    public String toString() {
        return "ApolloConfigurationCenterSettings(" + "apolloCluster=" + this.getApolloCluster() + ", clusterName=" + this
            .getClusterName() + ", period=" + this.getPeriod() + ")";
    }
}
