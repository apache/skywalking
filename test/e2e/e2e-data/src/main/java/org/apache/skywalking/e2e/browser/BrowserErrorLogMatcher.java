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

package org.apache.skywalking.e2e.browser;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.ToString;
import org.apache.skywalking.e2e.verification.AbstractMatcher;

import static java.util.Objects.nonNull;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
public class BrowserErrorLogMatcher extends AbstractMatcher<BrowserErrorLog> {
    private String service;
    private String serviceVersion;
    private String time;
    private String pagePath;
    private String category;
    private String grade;
    private String message;
    private String line;
    private String col;
    private String stack;
    private String errorUrl;
    private String firstReportedError;

    @Override
    public void verify(final BrowserErrorLog log) {
        if (nonNull(getService())) {
            doVerify(getService(), log.getService());
        }

        if (nonNull(getServiceVersion())) {
            doVerify(getServiceVersion(), log.getServiceVersion());
        }

        if (nonNull(getTime())) {
            doVerify(getTime(), String.valueOf(log.getTime()));
        }

        if (nonNull(getPagePath())) {
            doVerify(getPagePath(), log.getPagePath());
        }

        if (nonNull(getCategory())) {
            doVerify(getCategory(), log.getCategory());
        }

        if (nonNull(getGrade())) {
            doVerify(getGrade(), log.getGrade());
        }

        if (nonNull(getMessage())) {
            doVerify(getMessage(), log.getMessage());
        }

        if (nonNull(getLine())) {
            doVerify(getLine(), log.getLine());
        }

        if (nonNull(getCol())) {
            doVerify(getCol(), log.getCol());
        }

        if (nonNull(getStack())) {
            doVerify(getStack(), log.getStack());
        }

        if (nonNull(getErrorUrl())) {
            doVerify(getErrorUrl(), log.getErrorUrl());
        }

        if (nonNull(getFirstReportedError())) {
            doVerify(getFirstReportedError(), log.isFirstReportedError());
        }
    }
}
