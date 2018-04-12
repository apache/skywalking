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

package org.apache.skywalking.apm.collector.storage.table.segment;

import org.apache.skywalking.apm.collector.core.data.Column;
import org.apache.skywalking.apm.collector.core.data.StreamData;
import org.apache.skywalking.apm.collector.core.data.operator.CoverMergeOperation;
import org.apache.skywalking.apm.collector.core.data.operator.NonMergeOperation;

/**
 * @author peng-yongsheng
 */
public class SegmentDuration extends StreamData {

    private static final Column[] STRING_COLUMNS = {
        new Column(SegmentDurationTable.ID, new NonMergeOperation()),
        new Column(SegmentDurationTable.SEGMENT_ID, new CoverMergeOperation()),
        new Column(SegmentDurationTable.SERVICE_NAME, new CoverMergeOperation()),
        new Column(SegmentDurationTable.TRACE_ID, new CoverMergeOperation()),
    };

    private static final Column[] LONG_COLUMNS = {
        new Column(SegmentDurationTable.DURATION, new CoverMergeOperation()),
        new Column(SegmentDurationTable.START_TIME, new CoverMergeOperation()),
        new Column(SegmentDurationTable.END_TIME, new CoverMergeOperation()),
        new Column(SegmentDurationTable.TIME_BUCKET, new CoverMergeOperation()),
    };

    private static final Column[] DOUBLE_COLUMNS = {};

    private static final Column[] INTEGER_COLUMNS = {
        new Column(SegmentDurationTable.APPLICATION_ID, new CoverMergeOperation()),
        new Column(SegmentDurationTable.IS_ERROR, new CoverMergeOperation()),
    };

    private static final Column[] BYTE_COLUMNS = {};

    public SegmentDuration() {
        super(STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BYTE_COLUMNS);
    }

    @Override public String getId() {
        return getDataString(0);
    }

    @Override public void setId(String id) {
        setDataString(0, id);
    }

    @Override public String getMetricId() {
        return getId();
    }

    @Override public void setMetricId(String metricId) {
        setId(metricId);
    }

    public String getSegmentId() {
        return getDataString(1);
    }

    public void setSegmentId(String segmentId) {
        setDataString(1, segmentId);
    }

    public String getServiceName() {
        return getDataString(2);
    }

    public void setServiceName(String serviceName) {
        setDataString(2, serviceName);
    }

    public String getTraceId() {
        return getDataString(3);
    }

    public void setTraceId(String traceId) {
        setDataString(3, traceId);
    }

    public Long getDuration() {
        return getDataLong(0);
    }

    public void setDuration(Long duration) {
        setDataLong(0, duration);
    }

    public Long getStartTime() {
        return getDataLong(1);
    }

    public void setStartTime(Long startTime) {
        setDataLong(1, startTime);
    }

    public Long getEndTime() {
        return getDataLong(2);
    }

    public void setEndTime(Long endTime) {
        setDataLong(2, endTime);
    }

    public Long getTimeBucket() {
        return getDataLong(3);
    }

    public void setTimeBucket(Long timeBucket) {
        setDataLong(3, timeBucket);
    }

    public Integer getApplicationId() {
        return getDataInteger(0);
    }

    public void setApplicationId(Integer applicationId) {
        setDataInteger(0, applicationId);
    }

    public Integer getIsError() {
        return getDataInteger(1);
    }

    public void setIsError(Integer isError) {
        setDataInteger(1, isError);
    }
}
