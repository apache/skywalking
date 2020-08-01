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

package org.apache.skywalking.oap.server.receiver.browser.provider.parser.performance;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserPerfData;

@RequiredArgsConstructor
public class BrowserPerfDataDecorator {

    private boolean isOrigin = true;
    private final BrowserPerfData browserPerfData;
    private BrowserPerfData.Builder builder;

    public String getService() {
        return isOrigin ? browserPerfData.getService() : builder.getService();
    }

    public String getServiceVersion() {
        return isOrigin ? browserPerfData.getServiceVersion() : builder.getServiceVersion();
    }

    public long getTime() {
        return isOrigin ? browserPerfData.getTime() : builder.getTime();
    }

    public String getPagePath() {
        return isOrigin ? browserPerfData.getPagePath() : builder.getPagePath();
    }

    public int getRedirectTime() {
        return isOrigin ? browserPerfData.getRedirectTime() : builder.getRedirectTime();
    }

    public int getDnsTime() {
        return isOrigin ? browserPerfData.getDnsTime() : builder.getDnsTime();
    }

    public int getReqTime() {
        return isOrigin ? browserPerfData.getReqTime() : builder.getReqTime();
    }

    public int getDomAnalysisTime() {
        return isOrigin ? browserPerfData.getDomAnalysisTime() : builder.getDomAnalysisTime();
    }

    public int getDomReadyTime() {
        return isOrigin ? browserPerfData.getDomReadyTime() : builder.getDomReadyTime();
    }

    public int getBlankTime() {
        return isOrigin ? browserPerfData.getBlankTime() : builder.getBlankTime();
    }

    public void setTime(long time) {
        if (isOrigin) {
            toBuilder();
        }
        builder.setTime(time);
    }

    void toBuilder() {
        if (isOrigin) {
            this.isOrigin = false;
            this.builder = browserPerfData.toBuilder();
        }
    }
}
