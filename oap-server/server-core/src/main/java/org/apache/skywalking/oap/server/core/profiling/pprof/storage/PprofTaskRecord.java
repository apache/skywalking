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

package org.apache.skywalking.oap.server.core.profiling.pprof.storage;

import com.google.gson.Gson;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.ElasticSearch;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.PPROF_TASK;

/**
 * Pprof task database bean, use none stream
 */
@Getter
@Setter
@ScopeDeclaration(id = PPROF_TASK, name = "PprofTask")
@Stream(name = PprofTaskRecord.INDEX_NAME, scopeId = PPROF_TASK, builder = PprofTaskRecord.Builder.class, processor = NoneStreamProcessor.class)
@BanyanDB.TimestampColumn(PprofTaskRecord.CREATE_TIME)
@BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS)
public class PprofTaskRecord extends NoneStream {
    private static final Gson GSON = new Gson();

    public static final String INDEX_NAME = "pprof_task";
    public static final String TASK_ID = "task_id";
    public static final String SERVICE_ID = "service_id";
    public static final String SERVICE_INSTANCE_IDS = "service_instance_ids";
    public static final String CREATE_TIME = "create_time";
    public static final String EVENT_TYPES = "events";
    public static final String DURATION = "duration";
    public static final String DUMP_PERIOD = "dump_period";

    @Override
    public StorageID id() {
        return new StorageID().append(TASK_ID, taskId);
    }

    @Column(name = SERVICE_ID)
    @BanyanDB.SeriesID(index = 0)
    private String serviceId;
    @Column(name = SERVICE_INSTANCE_IDS)
    private String serviceInstanceIds;
    @Column(name = TASK_ID)
    private String taskId;
    @ElasticSearch.EnableDocValues
    @Column(name = CREATE_TIME)
    private long createTime;
    @ElasticSearch.EnableDocValues
    @Column(name = EVENT_TYPES)
    private String events;
    @Column(name = DURATION)
    private int duration;
    @Column(name = DUMP_PERIOD)
    private int dumpPeriod;

    public static class Builder implements StorageBuilder<PprofTaskRecord> {

        @Override
        public PprofTaskRecord storage2Entity(final Convert2Entity converter) {
            PprofTaskRecord record = new PprofTaskRecord();
            record.setServiceId((String) converter.get(SERVICE_ID));
            record.setServiceInstanceIds((String) converter.get(SERVICE_INSTANCE_IDS));
            record.setTaskId((String) converter.get(TASK_ID));
            record.setCreateTime(((Number) converter.get(CREATE_TIME)).longValue());
            record.setEvents((String) converter.get(EVENT_TYPES));
            record.setDuration(((Number) converter.get(DURATION)).intValue());
            record.setDumpPeriod(((Number) converter.get(DUMP_PERIOD)).intValue());
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return record;
        }

        @Override
        public void entity2Storage(final PprofTaskRecord storageData, final Convert2Storage converter) {
            converter.accept(SERVICE_ID, storageData.getServiceId());
            converter.accept(SERVICE_INSTANCE_IDS, storageData.getServiceInstanceIds());
            converter.accept(TASK_ID, storageData.getTaskId());
            converter.accept(CREATE_TIME, storageData.getCreateTime());
            converter.accept(EVENT_TYPES, storageData.getEvents());
            converter.accept(DURATION, storageData.getDuration());
            converter.accept(DUMP_PERIOD, storageData.getDumpPeriod());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }

    public void setServiceInstanceIdsFromList(List<String> serviceInstanceIds) {
        this.serviceInstanceIds = GSON.toJson(serviceInstanceIds);
    }

}
