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

import org.apache.skywalking.apm.collector.core.data.*;
import org.apache.skywalking.apm.collector.core.data.column.*;
import org.apache.skywalking.apm.collector.core.data.operator.*;

/**
 * @author peng-yongsheng
 */
public class SegmentDuration extends StreamData {

    private static final StringColumn[] STRING_COLUMNS = {
        new StringColumn(SegmentDurationTable.ID, new NonMergeOperation()),
        new StringColumn(SegmentDurationTable.SEGMENT_ID, new CoverMergeOperation()),
        new StringColumn(SegmentDurationTable.TRACE_ID, new CoverMergeOperation()),
    };

    private static final LongColumn[] LONG_COLUMNS = {
        new LongColumn(SegmentDurationTable.DURATION, new CoverMergeOperation()),
        new LongColumn(SegmentDurationTable.START_TIME, new CoverMergeOperation()),
        new LongColumn(SegmentDurationTable.END_TIME, new CoverMergeOperation()),
        new LongColumn(SegmentDurationTable.TIME_BUCKET, new CoverMergeOperation()),
    };

    private static final IntegerColumn[] INTEGER_COLUMNS = {
        new IntegerColumn(SegmentDurationTable.APPLICATION_ID, new CoverMergeOperation()),
        new IntegerColumn(SegmentDurationTable.IS_ERROR, new CoverMergeOperation()),
    };

    private static final StringListColumn[] STRING_LIST_COLUMNS = {
        new StringListColumn(SegmentDurationTable.SERVICE_NAME, new CoverMergeOperation()),
    };

    public SegmentDuration() {
        super(STRING_COLUMNS, LONG_COLUMNS, INTEGER_COLUMNS, new DoubleColumn[0], STRING_LIST_COLUMNS, new LongListColumn[0], new IntegerListColumn[0], new DoubleListColumn[0]);
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

    public String getTraceId() {
        return getDataString(2);
    }

    public void setTraceId(String traceId) {
        setDataString(2, traceId);
    }

    public StringLinkedList getServiceName() {
        return getDataStringList(0);
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
