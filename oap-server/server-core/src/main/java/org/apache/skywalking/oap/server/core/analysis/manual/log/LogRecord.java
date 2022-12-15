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

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.storage.ShardingAlgorithm;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.annotation.SuperDataset;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;

import static org.apache.skywalking.oap.server.core.analysis.manual.segment.SegmentRecord.SERVICE_ID;
import static org.apache.skywalking.oap.server.core.analysis.record.Record.TIME_BUCKET;

@SuperDataset
@Stream(name = LogRecord.INDEX_NAME, scopeId = DefaultScopeDefine.LOG, builder = LogRecord.Builder.class, processor = RecordStreamProcessor.class)
@SQLDatabase.ExtraColumn4AdditionalEntity(additionalTable = AbstractLogRecord.ADDITIONAL_TAG_TABLE, parentColumn = TIME_BUCKET)
@SQLDatabase.Sharding(shardingAlgorithm = ShardingAlgorithm.TIME_SEC_RANGE_SHARDING_ALGORITHM, dataSourceShardingColumn = SERVICE_ID, tableShardingColumn = TIME_BUCKET)
@BanyanDB.TimestampColumn(AbstractLogRecord.TIMESTAMP)
public class LogRecord extends AbstractLogRecord {

    public static final String INDEX_NAME = "log";

    public static final String UNIQUE_ID = "unique_id";

    @Setter
    @Getter
    @Column(columnName = UNIQUE_ID)
    private String uniqueId;

    @Override
    public StorageID id() {
        return new StorageID().append(UNIQUE_ID, uniqueId);
    }

    public static class Builder extends AbstractLogRecord.Builder<LogRecord> {
        @Override
        public LogRecord storage2Entity(final Convert2Entity converter) {
            LogRecord record = new LogRecord();
            map2Data(record, converter);
            record.setUniqueId((String) converter.get(UNIQUE_ID));
            return record;
        }

        @Override
        public void entity2Storage(final LogRecord record, final Convert2Storage converter) {
            data2Map(record, converter);
            converter.accept(UNIQUE_ID, record.getUniqueId());
        }
    }

}
