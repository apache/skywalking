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

package org.apache.skywalking.oap.server.core.analysis.manual.log;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.SuperDataset;

@SuperDataset
@Stream(name = LogRecord.INDEX_NAME, scopeId = DefaultScopeDefine.LOG, builder = LogRecord.Builder.class, processor = RecordStreamProcessor.class)
public class LogRecord extends AbstractLogRecord {

    public static final String INDEX_NAME = "log";

    public static final String UNIQUE_ID = "unique_id";

    @Setter
    @Getter
    @Column(columnName = UNIQUE_ID)
    private String uniqueId;

    @Override
    public String id() {
        return uniqueId;
    }

    public static class Builder extends AbstractLogRecord.Builder<LogRecord> {

        @Override
        public LogRecord storage2Entity(final Map<String, Object> dbMap) {
            LogRecord record = new LogRecord();
            map2Data(record, dbMap);
            record.setUniqueId((String) dbMap.get(UNIQUE_ID));
            return record;
        }

        @Override
        public Map<String, Object> entity2Storage(final LogRecord record) {
            Map<String, Object> dbMap = new HashMap<>();
            data2Map(dbMap, record);
            dbMap.put(UNIQUE_ID, record.getUniqueId());
            return dbMap;
        }
    }

}
