/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance.decorators;

import org.apache.skywalking.apm.network.language.agent.v3.BrowserPerfData;

public class BrowserPerfDataDecorator implements BrowserPerfDecorator {
    private BrowserPerfData.Builder builder;

    public BrowserPerfDataDecorator(final BrowserPerfData browserPerfData) {
        this.builder = browserPerfData.toBuilder();
    }

    public String getService() {
        return builder.getService();
    }

    public String getServiceVersion() {
        return builder.getServiceVersion();
    }

    public long getTime() {
        return builder.getTime();
    }

    public String getPagePath() {
        return builder.getPagePath();
    }

    public int getRedirectTime() {
        return builder.getRedirectTime();
    }

    public int getDnsTime() {
        return builder.getDnsTime();
    }

    public int getTtfbTime() {
        return builder.getTtfbTime();
    }

    public int getTcpTime() {
        return builder.getTcpTime();
    }

    public int getTransTime() {
        return builder.getTransTime();
    }

    public int getDomAnalysisTime() {
        return builder.getDomAnalysisTime();
    }

    public int getFptTime() {
        return builder.getFptTime();
    }

    public int getDomReadyTime() {
        return builder.getDomReadyTime();
    }

    public int getLoadPageTime() {
        return builder.getLoadPageTime();
    }

    public int getResTime() {
        return builder.getResTime();
    }

    public int getSslTime() {
        return builder.getSslTime();
    }

    public int getTtlTime() {
        return builder.getTtlTime();
    }

    public int getFirstPackTime() {
        return builder.getFirstPackTime();
    }

    public int getFmpTime() {
        return builder.getFmpTime();
    }

    public void setTime(long time) {
        builder.setTime(time);
    }

    public void setServiceVersion(String version) {
        builder.setServiceVersion(version);
    }

    public void setPagePath(String pagePath) {
        builder.setPagePath(pagePath);
    }
}
