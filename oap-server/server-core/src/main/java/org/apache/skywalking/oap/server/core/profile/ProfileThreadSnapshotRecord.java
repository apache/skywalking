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

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.QueryUnifiedIndex;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PROFILE_TASK_SEGMENT_SNAPSHOT;

/**
 * Profiling segment snapshot database bean, use record
 */
@Getter
@Setter
@ScopeDeclaration(id = PROFILE_TASK_SEGMENT_SNAPSHOT, name = "ProfileThreadSnapshot")
@Stream(name = ProfileThreadSnapshotRecord.INDEX_NAME, scopeId = PROFILE_TASK_SEGMENT_SNAPSHOT, builder = ProfileThreadSnapshotRecord.Builder.class, processor = RecordStreamProcessor.class)
public class ProfileThreadSnapshotRecord extends Record {

    public static final String INDEX_NAME = "profile_task_segment_snapshot";
    public static final String TASK_ID = "task_id";
    public static final String SEGMENT_ID = "segment_id";
    public static final String DUMP_TIME = "dump_time";
    public static final String SEQUENCE = "sequence";
    public static final String STACK_BINARY = "stack_binary";

    @Column(columnName = TASK_ID)
    @QueryUnifiedIndex(withColumns = {SEGMENT_ID})
    private String taskId;
    @Column(columnName = SEGMENT_ID)
    @QueryUnifiedIndex(withColumns = {SEQUENCE})
    @QueryUnifiedIndex(withColumns = {DUMP_TIME})
    private String segmentId;
    @Column(columnName = DUMP_TIME)
    private long dumpTime;
    @Column(columnName = SEQUENCE)
    private int sequence;
    @Column(columnName = STACK_BINARY)
    private byte[] stackBinary;

    @Override
    public String id() {
        return getTaskId() + Const.ID_CONNECTOR + getSegmentId() + Const.ID_CONNECTOR + getSequence() + Const.ID_CONNECTOR;
    }

    public static class Builder implements StorageBuilder<ProfileThreadSnapshotRecord> {

        @Override
        public ProfileThreadSnapshotRecord map2Data(Map<String, Object> dbMap) {
            final ProfileThreadSnapshotRecord snapshot = new ProfileThreadSnapshotRecord();
            snapshot.setTaskId((String) dbMap.get(TASK_ID));
            snapshot.setSegmentId((String) dbMap.get(SEGMENT_ID));
            snapshot.setDumpTime(((Number) dbMap.get(DUMP_TIME)).longValue());
            snapshot.setSequence(((Number) dbMap.get(SEQUENCE)).intValue());
            snapshot.setTimeBucket(((Number) dbMap.get(TIME_BUCKET)).intValue());
            if (StringUtil.isEmpty((String) dbMap.get(STACK_BINARY))) {
                snapshot.setStackBinary(new byte[] {});
            } else {
                snapshot.setStackBinary(Base64.getDecoder().decode((String) dbMap.get(STACK_BINARY)));
            }
            return snapshot;
        }

        @Override
        public Map<String, Object> data2Map(ProfileThreadSnapshotRecord storageData) {
            final HashMap<String, Object> map = new HashMap<>();
            map.put(TASK_ID, storageData.getTaskId());
            map.put(SEGMENT_ID, storageData.getSegmentId());
            map.put(DUMP_TIME, storageData.getDumpTime());
            map.put(SEQUENCE, storageData.getSequence());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            if (CollectionUtils.isEmpty(storageData.getStackBinary())) {
                map.put(STACK_BINARY, Const.EMPTY_STRING);
            } else {
                map.put(STACK_BINARY, new String(Base64.getEncoder().encode(storageData.getStackBinary())));
            }
            return map;
        }
    }
}
