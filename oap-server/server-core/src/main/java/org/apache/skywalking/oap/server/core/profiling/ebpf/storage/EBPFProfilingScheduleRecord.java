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

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.analysis.MetricsExtension;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.core.analysis.worker.MetricsStreamProcessor;
import org.apache.skywalking.oap.server.core.remote.grpc.proto.RemoteData;
import org.apache.skywalking.oap.server.core.storage.ShardingAlgorithm;
import org.apache.skywalking.oap.server.core.storage.StorageID;
import org.apache.skywalking.oap.server.core.storage.annotation.BanyanDB;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;
import org.apache.skywalking.oap.server.core.storage.annotation.SQLDatabase;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Entity;
import org.apache.skywalking.oap.server.core.storage.type.Convert2Storage;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.EBPF_PROFILING_SCHEDULE;

/**
 * One eBPF profiling schedule is belong one {@link EBPFProfilingTaskRecord}, one task could schedule multi times.
 * The schedule use the {@link #taskId}, {@link #processId}, {@link #startTime} as id
 * And combine all the same id schedule to update the schedule finish time
 */
@Setter
@Getter
@Stream(name = EBPFProfilingScheduleRecord.INDEX_NAME, scopeId = EBPF_PROFILING_SCHEDULE,
    builder = EBPFProfilingScheduleRecord.Builder.class, processor = MetricsStreamProcessor.class)
@MetricsExtension(supportDownSampling = false, supportUpdate = true)
@EqualsAndHashCode(of = {
    "taskId",
    "processId",
    "startTime",
})
@SQLDatabase.Sharding(shardingAlgorithm = ShardingAlgorithm.NO_SHARDING)
public class EBPFProfilingScheduleRecord extends Metrics {

    public static final String INDEX_NAME = "ebpf_profiling_schedule";
    public static final String TASK_ID = "task_id";
    public static final String PROCESS_ID = "process_id";
    public static final String START_TIME = "start_time";
    public static final String END_TIME = "end_time";
    public static final String EBPF_PROFILING_SCHEDULE_ID = "ebpf_profiling_schedule_id";

    @Column(columnName = TASK_ID, length = 600)
    private String taskId;
    @Column(columnName = PROCESS_ID, length = 600)
    private String processId;
    @Column(columnName = START_TIME)
    private long startTime;
    @Column(columnName = END_TIME)
    private long endTime;
    @Column(columnName = EBPF_PROFILING_SCHEDULE_ID)
    @BanyanDB.SeriesID(index = 0)
    private String scheduleId;

    @Override
    public boolean combine(Metrics metrics) {
        final EBPFProfilingScheduleRecord executeTraffic = (EBPFProfilingScheduleRecord) metrics;
        if (executeTraffic.getEndTime() > this.endTime) {
            this.endTime = executeTraffic.endTime;
        }
        return true;
    }

    @Override
    public void calculate() {
    }

    @Override
    public Metrics toHour() {
        return null;
    }

    @Override
    public Metrics toDay() {
        return null;
    }

    @Override
    protected StorageID id0() {
        return new StorageID().append(EBPF_PROFILING_SCHEDULE_ID, scheduleId);
    }

    @Override
    public void deserialize(RemoteData remoteData) {
        setTaskId(remoteData.getDataStrings(0));
        setProcessId(remoteData.getDataStrings(1));
        setScheduleId(remoteData.getDataStrings(2));
        setStartTime(remoteData.getDataLongs(0));
        setEndTime(remoteData.getDataLongs(1));
        setTimeBucket(remoteData.getDataLongs(2));
    }

    @Override
    public RemoteData.Builder serialize() {
        final RemoteData.Builder builder = RemoteData.newBuilder();
        builder.addDataStrings(taskId);
        builder.addDataStrings(processId);
        builder.addDataStrings(scheduleId);
        builder.addDataLongs(startTime);
        builder.addDataLongs(endTime);
        builder.addDataLongs(getTimeBucket());
        return builder;
    }

    @Override
    public int remoteHashCode() {
        return this.hashCode();
    }

    public static class Builder implements StorageBuilder<EBPFProfilingScheduleRecord> {

        @Override
        public EBPFProfilingScheduleRecord storage2Entity(final Convert2Entity converter) {
            final EBPFProfilingScheduleRecord executeTraffic = new EBPFProfilingScheduleRecord();
            executeTraffic.setTaskId((String) converter.get(TASK_ID));
            executeTraffic.setProcessId((String) converter.get(PROCESS_ID));
            executeTraffic.setScheduleId((String) converter.get(EBPF_PROFILING_SCHEDULE_ID));
            executeTraffic.setStartTime(((Number) converter.get(START_TIME)).longValue());
            executeTraffic.setEndTime(((Number) converter.get(END_TIME)).longValue());
            executeTraffic.setTimeBucket(((Number) converter.get(TIME_BUCKET)).longValue());
            return executeTraffic;
        }

        @Override
        public void entity2Storage(final EBPFProfilingScheduleRecord storageData, final Convert2Storage converter) {
            converter.accept(TASK_ID, storageData.getTaskId());
            converter.accept(PROCESS_ID, storageData.getProcessId());
            converter.accept(EBPF_PROFILING_SCHEDULE_ID, storageData.getScheduleId());
            converter.accept(START_TIME, storageData.getStartTime());
            converter.accept(END_TIME, storageData.getEndTime());
            converter.accept(TIME_BUCKET, storageData.getTimeBucket());
        }
    }
}
