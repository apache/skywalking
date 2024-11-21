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

package org.apache.skywalking.oap.server.core.profiling.trace;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROFILE_TASK_LOG;

/**
 * profile task log database bean, use record
 */
@Getter
@Setter
@ScopeDeclaration(id = PROFILE_TASK_LOG, name = "ProfileTaskLog")
@Stream(name = ProfileTaskLogRecord.INDEX_NAME, scopeId = PROFILE_TASK_LOG, builder = ProfileTaskLogRecord.Builder.class, processor = RecordStreamProcessor.class)
@BanyanDB.TimestampColumn(ProfileTaskLogRecord.TIMESTAMP)
public class ProfileTaskLogRecord extends Record {

    public static final String INDEX_NAME = "profile_task_log";
    public static final String TASK_ID = "task_id";
    public static final String INSTANCE_ID = "instance_id";
    public static final String OPERATION_TYPE = "operation_type";
    public static final String OPERATION_TIME = "operation_time";
    public static final String TIMESTAMP = "timestamp";

    @Column(name = TASK_ID)
    private String taskId;
    @Column(name = INSTANCE_ID)
    @BanyanDB.SeriesID(index = 0)
    private String instanceId;
    @Column(name = OPERATION_TYPE, storageOnly = true)
    private int operationType;
    @Column(name = OPERATION_TIME)
    private long operationTime;
    @Getter
    @Setter
    @ElasticSearch.EnableDocValues
    @Column(name = TIMESTAMP)
    private long timestamp;

    @Override
    public StorageID id() {
        return new StorageID()
            .append(TASK_ID, getTaskId())
            .append(INSTANCE_ID, getInstanceId())
            .append(OPERATION_TYPE, getOperationType())
            .append(OPERATION_TIME, getOperationTime());
    }

    public static class Builder implements StorageBuilder<ProfileTaskLogRecord> {
        @Override
        public ProfileTaskLogRecord storage2Entity(final Convert2Entity converter) {
            final ProfileTaskLogRecord log = new ProfileTaskLogRecord();
            log.setTaskId((String) converter.get(TASK_ID));
            log.setInstanceId((String) converter.get(INSTANCE_ID));
            log.setOperationType(((Number) converter.get(OPERATION_TYPE)).intValue());
            log.setOperationTime(((Number) converter.get(OPERATION_TIME)).longValue());
            log.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            log.setTimestamp(((Number) converter.get(TIMESTAMP)).longValue());
            return log;
        }

        @Override
        public void entity2Storage(final ProfileTaskLogRecord storageData, final Convert2Storage converter) {
            converter.accept(TASK_ID, storageData.getTaskId());
            converter.accept(INSTANCE_ID, storageData.getInstanceId());
            converter.accept(OPERATION_TYPE, storageData.getOperationType());
            converter.accept(OPERATION_TIME, storageData.getOperationTime());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(TIMESTAMP, storageData.getTimestamp());
        }
    }
}
