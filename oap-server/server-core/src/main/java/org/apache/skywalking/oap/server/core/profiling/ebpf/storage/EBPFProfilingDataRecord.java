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
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.EBPF_PROFILING_DATA;

/**
 * eBPF profiling reported data from the eBPF agent side
 */
@Data
@Stream(name = EBPFProfilingDataRecord.INDEX_NAME, scopeId = EBPF_PROFILING_DATA,
    builder = EBPFProfilingDataRecord.Builder.class, processor = RecordStreamProcessor.class)
@BanyanDB.TimestampColumn(EBPFProfilingDataRecord.UPLOAD_TIME)
public class EBPFProfilingDataRecord extends Record {

    public static final String INDEX_NAME = "ebpf_profiling_data";
    public static final String SCHEDULE_ID = "schedule_id";
    public static final String TASK_ID = "task_id";
    public static final String STACK_ID_LIST = "stack_id";
    public static final String TARGET_TYPE = "target_type";
    public static final String DATA_BINARY = "dump_binary";
    public static final String UPLOAD_TIME = "upload_time";

    @Column(name = TASK_ID, length = 600)
    @BanyanDB.SeriesID(index = 0)
    private String taskId;
    @Column(name = SCHEDULE_ID, length = 600)
    private String scheduleId;
    @Column(name = STACK_ID_LIST)
    private String stackIdList;
    @Column(name = TARGET_TYPE)
    private int targetType;
    @Column(name = DATA_BINARY, storageOnly = true)
    private byte[] dataBinary;
    @Column(name = UPLOAD_TIME)
    private long uploadTime;

    @Override
    public StorageID id() {
        return new StorageID().appendMutant(
            new String[] {
                SCHEDULE_ID,
                STACK_ID_LIST,
                UPLOAD_TIME
            },
            Hashing.sha256().newHasher()
                   .putString(scheduleId, Charsets.UTF_8)
                   .putString(stackIdList, Charsets.UTF_8)
                   .putLong(uploadTime)
                   .hash().toString()
        );
    }

    public static class Builder implements StorageBuilder<EBPFProfilingDataRecord> {

        @Override
        public EBPFProfilingDataRecord storage2Entity(final Convert2Entity converter) {
            final EBPFProfilingDataRecord dataTraffic = new EBPFProfilingDataRecord();
            dataTraffic.setScheduleId((String) converter.get(SCHEDULE_ID));
            dataTraffic.setTaskId((String) converter.get(TASK_ID));
            dataTraffic.setStackIdList((String) converter.get(STACK_ID_LIST));
            dataTraffic.setTargetType(((Number) converter.get(TARGET_TYPE)).intValue());
            dataTraffic.setDataBinary(converter.getBytes(DATA_BINARY));
            dataTraffic.setUploadTime(((Number) converter.get(UPLOAD_TIME)).longValue());
            dataTraffic.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return dataTraffic;
        }

        @Override
        public void entity2Storage(final EBPFProfilingDataRecord storageData, final Convert2Storage converter) {
            converter.accept(SCHEDULE_ID, storageData.getScheduleId());
            converter.accept(TASK_ID, storageData.getTaskId());
            converter.accept(STACK_ID_LIST, storageData.getStackIdList());
            converter.accept(TARGET_TYPE, storageData.getTargetType());
            converter.accept(DATA_BINARY, storageData.getDataBinary());
            converter.accept(UPLOAD_TIME, storageData.getUploadTime());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}
