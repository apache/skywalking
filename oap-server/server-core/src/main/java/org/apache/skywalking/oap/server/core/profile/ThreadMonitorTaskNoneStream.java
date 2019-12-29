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

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.core.Const;
import org.apache.skywalking.oap.server.core.analysis.Stream;
import org.apache.skywalking.oap.server.core.analysis.config.NoneStream;
import org.apache.skywalking.oap.server.core.analysis.worker.NoneStreamingProcessor;
import org.apache.skywalking.oap.server.core.source.ScopeDeclaration;
import org.apache.skywalking.oap.server.core.storage.StorageBuilder;
import org.apache.skywalking.oap.server.core.storage.annotation.Column;

import java.util.HashMap;
import java.util.Map;

import static org.apache.skywalking.oap.server.core.source.DefaultScopeDefine.THREAD_MONITOR_TASK;

/**
 * thread monitor task database bean, use none stream
 *
 * @author MrPro
 */
@Getter
@Setter
@ScopeDeclaration(id = THREAD_MONITOR_TASK, name = "ThreadMonitorTask")
@Stream(name = ThreadMonitorTaskNoneStream.INDEX_NAME, scopeId = THREAD_MONITOR_TASK, builder = ThreadMonitorTaskNoneStream.Builder.class, processor = NoneStreamingProcessor.class)
public class ThreadMonitorTaskNoneStream extends NoneStream {

    public static final String INDEX_NAME = "thread_monitor_task";
    public static final String SERVICE_ID = "service_id";
    public static final String ENDPOINT_NAME = "endpoint_name";
    public static final String MONITOR_START_TIME = "monitor_start_time";
    public static final String MONITOR_DURATION = "monitor_duration";
    public static final String MIN_DURATION_THRESHOLD = "min_duration_threshold";
    public static final String DUMP_PERIOD = "dump_period";
    public static final String CREATE_TIME = "create_time";

    @Override
    public String id() {
        return getCreateTime() + Const.ID_SPLIT + getServiceId();
    }

    @Column(columnName = SERVICE_ID) private int serviceId;
    @Column(columnName = ENDPOINT_NAME) private String endpointName;
    @Column(columnName = MONITOR_START_TIME) private long startTime;
    @Column(columnName = MONITOR_DURATION) private int duration;
    @Column(columnName = MIN_DURATION_THRESHOLD) private int minDurationThreshold;
    @Column(columnName = DUMP_PERIOD) private int dumpPeriod;
    @Column(columnName = CREATE_TIME) private long createTime;

    public static class Builder implements StorageBuilder<ThreadMonitorTaskNoneStream> {

        @Override
        public ThreadMonitorTaskNoneStream map2Data(Map<String, Object> dbMap) {
            final ThreadMonitorTaskNoneStream record = new ThreadMonitorTaskNoneStream();
            record.setServiceId(((Number)dbMap.get(SERVICE_ID)).intValue());
            record.setEndpointName((String)dbMap.get(ENDPOINT_NAME));
            record.setStartTime(((Number)dbMap.get(MONITOR_START_TIME)).longValue());
            record.setDuration(((Number)dbMap.get(MONITOR_DURATION)).intValue());
            record.setMinDurationThreshold(((Number)dbMap.get(MIN_DURATION_THRESHOLD)).intValue());
            record.setDumpPeriod(((Number)dbMap.get(DUMP_PERIOD)).intValue());
            record.setCreateTime(((Number)dbMap.get(CREATE_TIME)).longValue());
            record.setTimeBucket(((Number)dbMap.get(TIME_BUCKET)).longValue());
            return record;
        }

        @Override
        public Map<String, Object> data2Map(ThreadMonitorTaskNoneStream storageData) {
            final HashMap<String, Object> map = new HashMap<>();
            map.put(SERVICE_ID, storageData.getServiceId());
            map.put(ENDPOINT_NAME, storageData.getEndpointName());
            map.put(MONITOR_START_TIME, storageData.getStartTime());
            map.put(MONITOR_DURATION, storageData.getDuration());
            map.put(MIN_DURATION_THRESHOLD, storageData.getMinDurationThreshold());
            map.put(DUMP_PERIOD, storageData.getDumpPeriod());
            map.put(CREATE_TIME, storageData.getCreateTime());
            map.put(TIME_BUCKET, storageData.getTimeBucket());
            return map;
        }
    }

}
