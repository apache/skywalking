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

package org.apache.skywalking.oap.server.core.profiling.ebpf.storage;

import com.google.common.hash.Hashing;
import com.linecorp.armeria.internal.shaded.guava.base.Charsets;
import lombok.Data;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.record.Record;
import org.apache.skywalking.oap.server.core.analysis.worker.RecordStreamProcessor;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.HashMapConverter;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.EBPF_PROFILING_DATA;

/**
 * eBPF profiling reported data from the eBPF agent side
 */
@Data
@Stream(name = EBPFProfilingDataRecord.INDEX_NAME, scopeId = EBPF_PROFILING_DATA,
        builder = EBPFProfilingDataRecord.Builder.class, processor = RecordStreamProcessor.class)
public class EBPFProfilingDataRecord extends Record {

    public static final String INDEX_NAME = "ebpf_profiling_data";
    public static final String SCHEDULE_ID = "schedule_id";
    public static final String TASK_ID = "task_id";
    public static final String STACK_ID_LIST = "stack_id";
    public static final String STACK_DUMP_COUNT = "dump_count";
    public static final String STACKS_BINARY = "dump_binary";
    public static final String UPLOAD_TIME = "upload_time";

    @Column(columnName = TASK_ID, length = 600, shardingKeyIdx = 0)
    private String taskId;
    @Column(columnName = SCHEDULE_ID, length = 600)
    private String scheduleId;
    @Column(columnName = STACK_ID_LIST)
    private String stackIdList;
    @Column(columnName = STACKS_BINARY, storageOnly = true)
    private byte[] stacksBinary;
    @Column(columnName = STACK_DUMP_COUNT)
    private long stackDumpCount;
    @Column(columnName = UPLOAD_TIME)
    private long uploadTime;

    @Override
    public String id() {
        return Hashing.sha256().newHasher()
                .putString(scheduleId, Charsets.UTF_8)
                .putString(stackIdList, Charsets.UTF_8)
                .putLong(uploadTime)
                .hash().toString();
    }

    public static class Builder implements StorageBuilder<EBPFProfilingDataRecord> {

        @Override
        public EBPFProfilingDataRecord storage2Entity(final Convert2Entity converter) {
            final EBPFProfilingDataRecord dataTraffic = new EBPFProfilingDataRecord();
            dataTraffic.setScheduleId((String) converter.get(SCHEDULE_ID));
            dataTraffic.setTaskId((String) converter.get(TASK_ID));
            dataTraffic.setStackIdList((String) converter.get(STACK_ID_LIST));
            dataTraffic.setStacksBinary(converter.getWith(STACKS_BINARY, HashMapConverter.ToEntity.Base64Decoder.INSTANCE));
            dataTraffic.setStackDumpCount(((Number) converter.get(STACK_DUMP_COUNT)).longValue());
            dataTraffic.setUploadTime(((Number) converter.get(UPLOAD_TIME)).longValue());
            dataTraffic.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return dataTraffic;
        }

        @Override
        public void entity2Storage(final EBPFProfilingDataRecord storageData, final Convert2Storage converter) {
            converter.accept(SCHEDULE_ID, storageData.getScheduleId());
            converter.accept(TASK_ID, storageData.getTaskId());
            converter.accept(STACK_ID_LIST, storageData.getStackIdList());
            converter.accept(STACKS_BINARY, storageData.getStacksBinary());
            converter.accept(STACK_DUMP_COUNT, storageData.getStackDumpCount());
            converter.accept(UPLOAD_TIME, storageData.getUploadTime());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}