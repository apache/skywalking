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

package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.ErrorCategory;

import java.util.Collections;
import java.util.List;

public class BrowserErrorLogDeserializer extends AbstractBanyanDBDeserializer<BrowserErrorLog> {
    public BrowserErrorLogDeserializer() {
        super(BrowserErrorLogRecord.INDEX_NAME,
                ImmutableList.of(BrowserErrorLogRecord.SERVICE_ID,
                        BrowserErrorLogRecord.SERVICE_VERSION_ID,
                        BrowserErrorLogRecord.PAGE_PATH_ID,
                        BrowserErrorLogRecord.ERROR_CATEGORY),
                Collections.singletonList(BrowserErrorLogRecord.DATA_BINARY));
    }

    @Override
    public BrowserErrorLog map(RowEntity row) {
        // FIXME: use protobuf directly
        BrowserErrorLog log = new BrowserErrorLog();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        log.setService((String) searchable.get(0).getValue());
        log.setServiceVersion((String) searchable.get(1).getValue());
        log.setPagePath((String) searchable.get(2).getValue());
        log.setCategory(ErrorCategory.valueOf((String) searchable.get(3).getValue()));
        log.setTime(row.getTimestamp());
        final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
        Object o = data.get(0).getValue();
        if (o instanceof ByteString && !((ByteString) o).isEmpty()) {
            try {
                org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog browserErrorLog = org.apache.skywalking.apm.network.language.agent.v3.BrowserErrorLog
                        .parseFrom((ByteString) o);
                log.setGrade(browserErrorLog.getGrade());
                log.setCol(browserErrorLog.getCol());
                log.setLine(browserErrorLog.getLine());
                log.setMessage(browserErrorLog.getMessage());
                log.setErrorUrl(browserErrorLog.getErrorUrl());
                log.setStack(browserErrorLog.getStack());
                log.setFirstReportedError(browserErrorLog.getFirstReportedError());
            } catch (InvalidProtocolBufferException ex) {
                throw new RuntimeException("fail to parse proto buffer", ex);
            }
        }
        return log;
    }
}
