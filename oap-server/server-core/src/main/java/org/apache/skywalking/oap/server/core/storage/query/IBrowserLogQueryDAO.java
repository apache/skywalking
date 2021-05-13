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

package org.apache.skywalking.oap.server.core.storage.query;

import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.util.Base64;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.query.type.ErrorCategory;
import org.apache.skywalking.oap.server.library.module.Service;

public interface IBrowserLogQueryDAO extends Service {
    BrowserErrorLogs queryBrowserErrorLogs(String serviceId,
                                           String serviceVersionId,
                                           String pagePathId,
                                           String pagePath,
                                           BrowserErrorCategory category,
                                           long startSecondTB,
                                           long endSecondTB,
                                           int limit,
                                           int from) throws IOException;

    /**
     * Parser the raw error log.
     */
    default BrowserErrorLog parserDataBinary(
        String dataBinaryBase64) {
        try {
            BrowserErrorLog log = new BrowserErrorLog();
            org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog browserErrorLog = org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog
                .parseFrom(Base64.getDecoder().decode(dataBinaryBase64));

            log.setService(browserErrorLog.getService());
            log.setServiceVersion(browserErrorLog.getServiceVersion());
            log.setTime(browserErrorLog.getTime());
            log.setPagePath(browserErrorLog.getPagePath());
            log.setCategory(ErrorCategory.valueOf(browserErrorLog.getCategory().name().toUpperCase()));
            log.setGrade(browserErrorLog.getGrade());
            log.setMessage(browserErrorLog.getMessage());
            log.setLine(browserErrorLog.getLine());
            log.setCol(browserErrorLog.getCol());
            log.setStack(browserErrorLog.getStack());
            log.setErrorUrl(browserErrorLog.getErrorUrl());
            log.setFirstReportedError(browserErrorLog.getFirstReportedError());

            return log;
        } catch (InvalidProtocolBufferException e) {
            throw new RuntimeException(e);
        }
    }
}
