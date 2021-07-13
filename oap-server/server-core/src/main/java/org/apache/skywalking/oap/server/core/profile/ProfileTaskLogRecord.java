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

package org.apache.skywalking.oap.server.core.profile;

import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageHashMapBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROFILE_TASK_LOG;

/**
 * profile task log database bean, use record
 */
@Getter
@Setter
@ScopeDeclaration(id = PROFILE_TASK_LOG, name = "ProfileTaskLog")
@Stream(name = ProfileTaskLogRecord.INDEX_NAME, scopeId = PROFILE_TASK_LOG, builder = ProfileTaskLogRecord.Builder.class, processor = RecordStreamProcessor.class)
public class ProfileTaskLogRecord extends Record {

    public static final String INDEX_NAME = "profile_task_log";
    public static final String TASK_ID = "task_id";
    public static final String INSTANCE_ID = "instance_id";
    public static final String OPERATION_TYPE = "operation_type";
    public static final String OPERATION_TIME = "operation_time";

    @Column(columnName = TASK_ID, storageOnly = true)
    private String taskId;
    @Column(columnName = INSTANCE_ID, storageOnly = true)
    private String instanceId;
    @Column(columnName = OPERATION_TYPE, storageOnly = true)
    private int operationType;
    @Column(columnName = OPERATION_TIME)
    private long operationTime;

    @Override
    public String id() {
        return getTaskId() + Const.ID_CONNECTOR + getInstanceId() + Const.ID_CONNECTOR + getOperationType() + Const.ID_CONNECTOR + getOperationTime();
    }

    public static class Builder implements StorageHashMapBuilder<ProfileTaskLogRecord> {

        @Override
        public ProfileTaskLogRecord storage2Entity(Map<String, Object> dbMap) {
            final ProfileTaskLogRecord log = new ProfileTaskLogRecord();
            log.setTaskId((String) dbMap.get(TASK_ID));
            log.setInstanceId((String) dbMap.get(INSTANCE_ID));
            log.setOperationType(((Number) dbMap.get(OPERATION_TYPE)).intValue());
            log.setOperationTime(((Number) dbMap.get(OPERATION_TIME)).longValue());
            log.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).longValue());
            return log;
        }

        @Override
        public Map<String, Object> entity2Storage(ProfileTaskLogRecord storageData) {
            final HashMap<String, Object> map = new HashMap<>();
            map.put(TASK_ID, storageData.getTaskId());
            map.put(INSTANCE_ID, storageData.getInstanceId());
            map.put(OPERATION_TYPE, storageData.getOperationType());
            map.put(OPERATION_TIME, storageData.getOperationTime());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            return map;
        }
    }
}
