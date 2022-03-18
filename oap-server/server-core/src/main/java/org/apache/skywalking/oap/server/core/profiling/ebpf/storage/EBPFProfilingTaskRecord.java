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
import org.apache.skywalking.oap.server.core.query.type.EBPFProfilingProcessFinderType;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
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
public class EBPFProfilingTaskRecord extends NoneStream {

    public static final String INDEX_NAME = "ebpf_profiling_task";
    public static final String PROCESS_FIND_TYPE = "process_find_type";
    public static final String SERVICE_ID = "service_id";
    public static final String INSTANCE_ID = "instance_id";
    public static final String PROCESS_ID = "process_id";
    public static final String PROCESS_NAME = "process_name";
    public static final String START_TIME = "start_time";
    public static final String TRIGGER_TYPE = "trigger_type";
    public static final String FIXED_TRIGGER_DURATION = "fixed_trigger_duration";
    public static final String TARGET_TYPE = "target_type";
    public static final String CREATE_TIME = "create_time";
    public static final String LAST_UPDATE_TIME = "last_update_time";

    @Column(columnName = PROCESS_FIND_TYPE)
    private int processFindType = EBPFProfilingProcessFinderType.UNKNOWN.value();
    @Column(columnName = SERVICE_ID)
    private String serviceId;
    @Column(columnName = INSTANCE_ID, length = 600)
    private String instanceId;
    @Column(columnName = PROCESS_ID, length = 600)
    private String processId;
    @Column(columnName = PROCESS_NAME, length = 500)
    private String processName;
    @Column(columnName = START_TIME)
    private long startTime;
    @Column(columnName = TRIGGER_TYPE)
    private int triggerType = EBPFProfilingTriggerType.UNKNOWN.value();
    @Column(columnName = FIXED_TRIGGER_DURATION)
    private long fixedTriggerDuration;
    @Column(columnName = TARGET_TYPE)
    private int targetType = EBPFProfilingTargetType.UNKNOWN.value();
    @Column(columnName = CREATE_TIME)
    private long createTime;
    @Column(columnName = LAST_UPDATE_TIME)
    private long lastUpdateTime;

    @Override
    public String id() {
        return Hashing.sha256().newHasher()
                .putString(processId, Charsets.UTF_8)
                .putLong(createTime)
                .putInt(processFindType)
                .hash().toString();
    }

    public static class Builder implements StorageBuilder<EBPFProfilingTaskRecord> {

        @Override
        public EBPFProfilingTaskRecord storage2Entity(final Convert2Entity converter) {
            final EBPFProfilingTaskRecord record = new EBPFProfilingTaskRecord();
            record.setProcessFindType(((Number) converter.get(PROCESS_FIND_TYPE)).intValue());
            record.setServiceId((String) converter.get(SERVICE_ID));
            record.setInstanceId((String) converter.get(INSTANCE_ID));
            record.setProcessId((String) converter.get(PROCESS_ID));
            record.setProcessName((String) converter.get(PROCESS_NAME));
            record.setTriggerType(((Number) converter.get(TRIGGER_TYPE)).intValue());
            record.setStartTime(((Number) converter.get(START_TIME)).longValue());
            record.setFixedTriggerDuration(((Number) converter.get(FIXED_TRIGGER_DURATION)).longValue());
            record.setTargetType(((Number) converter.get(TARGET_TYPE)).intValue());
            record.setCreateTime(((Number) converter.get(CREATE_TIME)).longValue());
            record.setLastUpdateTime(((Number) converter.get(LAST_UPDATE_TIME)).longValue());
            record.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return record;
        }

        @Override
        public void entity2Storage(final EBPFProfilingTaskRecord storageData, final Convert2Storage converter) {
            converter.accept(PROCESS_FIND_TYPE, storageData.getProcessFindType());
            converter.accept(SERVICE_ID, storageData.getServiceId());
            converter.accept(INSTANCE_ID, storageData.getInstanceId());
            converter.accept(PROCESS_ID, storageData.getProcessId());
            converter.accept(PROCESS_NAME, storageData.getProcessName());
            converter.accept(TRIGGER_TYPE, storageData.getTriggerType());
            converter.accept(START_TIME, storageData.getStartTime());
            converter.accept(FIXED_TRIGGER_DURATION, storageData.getFixedTriggerDuration());
            converter.accept(TARGET_TYPE, storageData.getTargetType());
            converter.accept(CREATE_TIME, storageData.getCreateTime());
            converter.accept(LAST_UPDATE_TIME, storageData.getLastUpdateTime());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}
