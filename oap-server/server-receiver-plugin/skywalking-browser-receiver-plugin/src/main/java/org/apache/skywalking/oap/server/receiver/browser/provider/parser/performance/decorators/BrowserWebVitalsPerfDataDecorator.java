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

package org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.decorators;

import org.apache.skywalking.apm.network.language.agent.v3.BrowserWebVitalsPerfData;

public class BrowserWebVitalsPerfDataDecorator implements BrowserPerfDecorator {
    private BrowserWebVitalsPerfData.Builder builder;

    public BrowserWebVitalsPerfDataDecorator(BrowserWebVitalsPerfData builder) {
        this.builder = builder.toBuilder();
    }

    @Override
    public String getService() {
        return builder.getService();
    }

    @Override
    public void setTime(long time) {
        builder.setTime(time);
    }

    @Override
    public String getServiceVersion() {
        return builder.getServiceVersion();
    }

    @Override
    public void setServiceVersion(String serviceVersion) {
        builder.setServiceVersion(serviceVersion);
    }

    @Override
    public String getPagePath() {
        return builder.getPagePath();
    }

    @Override
    public void setPagePath(String pagePath) {
        builder.setPagePath(pagePath);
    }

    public long getTime() {
        return builder.getTime();
    }

    public int getFmpTime() {
        return builder.getFmpTime();
    }

    public int getLcpTime() {
        return builder.getLcpTime();
    }

    public double getCls() {
        return builder.getCls();
    }
}
