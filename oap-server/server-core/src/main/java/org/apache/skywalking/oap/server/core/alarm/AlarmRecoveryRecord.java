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

package org.apache.skywalking.oap.server.core.alarm;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.DefaultScopeDefine;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.ALARM_RECOVERY;

@Getter
@Setter
@ScopeDeclaration(id = ALARM_RECOVERY, name = "AlarmRecovery")
@Stream(name = AlarmRecoveryRecord.INDEX_NAME, scopeId = DefaultScopeDefine.ALARM_RECOVERY, builder = AlarmRecoveryRecord.Builder.class, processor = RecordStreamProcessor.class)
@BanyanDB.TimestampColumn(AlarmRecoveryRecord.RECOVERY_TIME)
@BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS)
public class AlarmRecoveryRecord extends Record {
    public static final String INDEX_NAME = "alarm_recovery_record";
    public static final String UUID = "uuid";
    public static final String RECOVERY_TIME = "recovery_time";

    @Override
    public StorageID id() {
        return new StorageID().append(UUID, uuid);
    }

    @ElasticSearch.EnableDocValues
    @Column(name = RECOVERY_TIME)
    private long recoveryTime;
    @BanyanDB.SeriesID(index = 0)
    @Column(name = UUID)
    private String uuid;

    public static class Builder implements StorageBuilder<AlarmRecoveryRecord> {
        @Override
        public AlarmRecoveryRecord storage2Entity(final Convert2Entity converter) {
            AlarmRecoveryRecord record = new AlarmRecoveryRecord();
            record.setUuid((String) converter.get(UUID));
            record.setRecoveryTime(((Number) converter.get(RECOVERY_TIME)).longValue());
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return record;
        }

        @Override
        public void entity2Storage(final AlarmRecoveryRecord storageData, final Convert2Storage converter) {
            converter.accept(UUID, storageData.getUuid());
            converter.accept(RECOVERY_TIME, storageData.getRecoveryTime());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}
