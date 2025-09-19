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

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import lombok.Data;
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

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.EBPF_PROFILING_TASK;

/**
 * eBPF Profiling Task is the user create it from the UI side
 */
@Data
@ScopeDeclaration(id = EBPF_PROFILING_TASK, name = "EBPFProfilingTask")
@Stream(name = EBPFProfilingTaskRecord.INDEX_NAME, scopeId = EBPF_PROFILING_TASK,
    builder = EBPFProfilingTaskRecord.Builder.class, processor = NoneStreamProcessor.class)
@BanyanDB.TimestampColumn(EBPFProfilingTaskRecord.CREATE_TIME)
@BanyanDB.Group(streamGroup = BanyanDB.StreamGroup.RECORDS)
public class EBPFProfilingTaskRecord extends NoneStream {
    public static final String INDEX_NAME = "ebpf_profiling_task";
    public static final String LOGICAL_ID = "logical_id";
    public static final String SERVICE_ID = "service_id";
    public static final String PROCESS_LABELS_JSON = "process_labels_json";
    public static final String INSTANCE_ID = "instance_id";
    public static final String START_TIME = "start_time";
    public static final String TRIGGER_TYPE = "trigger_type";
    public static final String FIXED_TRIGGER_DURATION = "fixed_trigger_duration";
    public static final String TARGET_TYPE = "target_type";
    public static final String CREATE_TIME = "create_time";
    public static final String LAST_UPDATE_TIME = "last_update_time";
    public static final String EXTENSION_CONFIG_JSON = "extension_config_json";
    public static final String CONTINUOUS_PROFILING_JSON = "continuous_profiling_json";

    public static final int PROCESS_LABELS_JSON_MAX_LENGTH = 1000;
    public static final int EXTENSION_CONFIG_JSON_MAX_LENGTH = 1000;
    public static final int CONTINOUS_PROFILING_JSON_MAX_LENGTH = 1000;

    @Column(name = LOGICAL_ID)
    private String logicalId;
    @Column(name = SERVICE_ID)
    @BanyanDB.SeriesID(index = 0)
    private String serviceId;
    @Column(name = PROCESS_LABELS_JSON, length = PROCESS_LABELS_JSON_MAX_LENGTH)
    private String processLabelsJson;
    @Column(name = INSTANCE_ID, length = 512)
    private String instanceId;
    @ElasticSearch.EnableDocValues
    @Column(name = START_TIME)
    private long startTime;
    @Column(name = TRIGGER_TYPE)
    private int triggerType = EBPFProfilingTriggerType.UNKNOWN.value();
    @Column(name = FIXED_TRIGGER_DURATION)
    private long fixedTriggerDuration;
    @Column(name = TARGET_TYPE)
    private int targetType = EBPFProfilingTargetType.UNKNOWN.value();
    @ElasticSearch.EnableDocValues
    @Column(name = CREATE_TIME)
    @BanyanDB.NoIndexing
    private long createTime;
    @Column(name = LAST_UPDATE_TIME)
    private long lastUpdateTime;
    @Column(name = EXTENSION_CONFIG_JSON, length = EXTENSION_CONFIG_JSON_MAX_LENGTH, storageOnly = true)
    private String extensionConfigJson;
    @Column(name = CONTINUOUS_PROFILING_JSON, length = CONTINOUS_PROFILING_JSON_MAX_LENGTH, storageOnly = true)
    private String continuousProfilingJson;

    @Override
    public StorageID id() {
        return new StorageID().append(
            Hashing.sha256().newHasher()
                   .putString(logicalId, Charsets.UTF_8)
                   .putLong(createTime)
                   .hash().toString()
        );
    }

    /**
     * Generate the logical id and put it into record
     */
    public void generateLogicalId() {
        this.logicalId = Hashing.sha256().newHasher()
                                .putString(serviceId, Charsets.UTF_8)
                                .putString(processLabelsJson, Charsets.UTF_8)
                                .putLong(startTime)
                                .hash().toString();
    }

    /**
     * combine the same task
     * @param task have same {@link #logicalId}
     */
    public EBPFProfilingTaskRecord combine(EBPFProfilingTaskRecord task) {
        if (task.getFixedTriggerDuration() > this.getFixedTriggerDuration()) {
            this.setFixedTriggerDuration(task.getFixedTriggerDuration());
        }
        if (task.getLastUpdateTime() > this.getLastUpdateTime()) {
            this.setLastUpdateTime(task.getLastUpdateTime());
        }
        return this;
    }

    public static class Builder implements StorageBuilder<EBPFProfilingTaskRecord> {

        @Override
        public EBPFProfilingTaskRecord storage2Entity(final Convert2Entity converter) {
            final EBPFProfilingTaskRecord record = new EBPFProfilingTaskRecord();
            record.setLogicalId((String) converter.get(LOGICAL_ID));
            record.setServiceId((String) converter.get(SERVICE_ID));
            record.setProcessLabelsJson((String) converter.get(PROCESS_LABELS_JSON));
            record.setInstanceId((String) converter.get(INSTANCE_ID));
            record.setTriggerType(((Number) converter.get(TRIGGER_TYPE)).intValue());
            record.setStartTime(((Number) converter.get(START_TIME)).longValue());
            record.setFixedTriggerDuration(((Number) converter.get(FIXED_TRIGGER_DURATION)).longValue());
            record.setTargetType(((Number) converter.get(TARGET_TYPE)).intValue());
            record.setCreateTime(((Number) converter.get(CREATE_TIME)).longValue());
            record.setLastUpdateTime(((Number) converter.get(LAST_UPDATE_TIME)).longValue());
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            record.setExtensionConfigJson((String) converter.get(EXTENSION_CONFIG_JSON));
            record.setContinuousProfilingJson((String) converter.get(CONTINUOUS_PROFILING_JSON));
            return record;
        }

        @Override
        public void entity2Storage(final EBPFProfilingTaskRecord storageData, final Convert2Storage converter) {
            converter.accept(LOGICAL_ID, storageData.getLogicalId());
            converter.accept(SERVICE_ID, storageData.getServiceId());
            converter.accept(PROCESS_LABELS_JSON, storageData.getProcessLabelsJson());
            converter.accept(INSTANCE_ID, storageData.getInstanceId());
            converter.accept(TRIGGER_TYPE, storageData.getTriggerType());
            converter.accept(START_TIME, storageData.getStartTime());
            converter.accept(FIXED_TRIGGER_DURATION, storageData.getFixedTriggerDuration());
            converter.accept(TARGET_TYPE, storageData.getTargetType());
            converter.accept(CREATE_TIME, storageData.getCreateTime());
            converter.accept(LAST_UPDATE_TIME, storageData.getLastUpdateTime());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
            converter.accept(EXTENSION_CONFIG_JSON, storageData.getExtensionConfigJson());
            converter.accept(CONTINUOUS_PROFILING_JSON, storageData.getContinuousProfilingJson());
        }
    }
}
