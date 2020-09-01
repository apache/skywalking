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

package org.apache.skywalking.oap.server.receiver.browser.provider.parser.errorlog;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog;
import org.apache.skywalking.apm.network.language.agent.v3.ErrorCategory;

@RequiredArgsConstructor
public class BrowserErrorLogDecorator {
    private boolean isOrigin = true;
    private final BrowserErrorLog errorLog;
    private BrowserErrorLog.Builder builder;

    public String getUniqueId() {
        return isOrigin ? errorLog.getUniqueId() : builder.getUniqueId();
    }

    public String getService() {
        return isOrigin ? errorLog.getService() : builder.getService();
    }

    public String getServiceVersion() {
        return isOrigin ? errorLog.getServiceVersion() : builder.getServiceVersion();
    }

    public long getTime() {
        return isOrigin ? errorLog.getTime() : builder.getTime();
    }

    public String getPagePath() {
        return isOrigin ? errorLog.getPagePath() : builder.getPagePath();
    }

    public ErrorCategory getCategory() {
        return isOrigin ? errorLog.getCategory() : builder.getCategory();
    }

    public String getGrade() {
        return isOrigin ? errorLog.getGrade() : builder.getGrade();
    }

    public String getMessage() {
        return isOrigin ? errorLog.getMessage() : builder.getMessage();
    }

    public int getLine() {
        return isOrigin ? errorLog.getLine() : builder.getLine();
    }

    public int getCol() {
        return isOrigin ? errorLog.getCol() : builder.getCol();
    }

    public String getStack() {
        return isOrigin ? errorLog.getStack() : builder.getStack();
    }

    public String getErrorUrl() {
        return isOrigin ? errorLog.getErrorUrl() : builder.getErrorUrl();
    }

    public boolean isFirstReportedError() {
        return isOrigin ? errorLog.getFirstReportedError() : builder.getFirstReportedError();
    }

    public byte[] toByteArray() {
        return isOrigin ? errorLog.toByteArray() : builder.build().toByteArray();
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
            this.builder = errorLog.toBuilder();
        }
    }
}
