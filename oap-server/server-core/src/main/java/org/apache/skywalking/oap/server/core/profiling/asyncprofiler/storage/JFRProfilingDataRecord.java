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

package org.apache.skywalking.oap.server.core.profiling.asyncprofiler.storage;

import com.google.common.hash.Hashing;
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

import java.nio.charset.StandardCharsets;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.JFR_PROFILING_DATA;

@Data
@Stream(name = JFRProfilingDataRecord.INDEX_NAME, scopeId = JFR_PROFILING_DATA,
        builder = JFRProfilingDataRecord.Builder.class, processor = RecordStreamProcessor.class)
@BanyanDB.TimestampColumn(JFRProfilingDataRecord.UPLOAD_TIME)
public class JFRProfilingDataRecord extends Record {
    public static final String INDEX_NAME = "jfr_profiling_data";

    public static final String TASK_ID = "task_id";
    public static final String EVENT_TYPE = "event_type";
    public static final String INSTANCE_ID = "instance_id";
    public static final String DATA_BINARY = "data_binary";
    public static final String UPLOAD_TIME = "upload_time";

    @Column(name = TASK_ID)
    private String taskId;

    @Column(name = INSTANCE_ID)
    @BanyanDB.SeriesID(index = 0)
    private String instanceId;

    /**
     * @see org.apache.skywalking.oap.server.library.jfr.parser.JFREventType
     */
    @Column(name = EVENT_TYPE)
    private String eventType;

    @Column(name = UPLOAD_TIME)
    private long uploadTime;

    /**
     * @see org.apache.skywalking.oap.server.library.jfr.parser.FrameTree
     */
    @Column(name = DATA_BINARY, storageOnly = true)
    private byte[] dataBinary;

    @Override
    public StorageID id() {
        return new StorageID().appendMutant(
                new String[]{
                        TASK_ID,
                        INSTANCE_ID,
                        EVENT_TYPE,
                        UPLOAD_TIME
                },
                Hashing.sha256().newHasher()
                        .putString(taskId, StandardCharsets.UTF_8)
                        .putString(instanceId, StandardCharsets.UTF_8)
                        .putString(eventType, StandardCharsets.UTF_8)
                        .putLong(uploadTime)
                        .hash().toString()
        );
    }

    public static class Builder implements StorageBuilder<JFRProfilingDataRecord> {

        @Override
        public JFRProfilingDataRecord storage2Entity(final Convert2Entity converter) {
            final JFRProfilingDataRecord dataTraffic = new JFRProfilingDataRecord();
            dataTraffic.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            dataTraffic.setTaskId((String) converter.get(TASK_ID));
            dataTraffic.setInstanceId((String) converter.get(INSTANCE_ID));
            dataTraffic.setUploadTime(((Number) converter.get(UPLOAD_TIME)).longValue());
            dataTraffic.setEventType((String) converter.get(EVENT_TYPE));
            dataTraffic.setDataBinary(converter.getBytes(DATA_BINARY));
            return dataTraffic;
        }

        @Override
        public void entity2Storage(final JFRProfilingDataRecord storageData, final Convert2Storage converter) {
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(TASK_ID, storageData.getTaskId());
            converter.accept(INSTANCE_ID, storageData.getInstanceId());
            converter.accept(UPLOAD_TIME, storageData.getUploadTime());
            converter.accept(EVENT_TYPE, storageData.getEventType());
            converter.accept(DATA_BINARY, storageData.getDataBinary());
        }
    }

}
