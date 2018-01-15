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
import org.apache.skywalking.apm.collector.core.data.operator.CoverOperation;
import org.apache.skywalking.apm.collector.core.data.operator.NonOperation;

/**
 * @author peng-yongsheng
 */
public class SegmentCost extends StreamData {

    private static final Column[] STRING_COLUMNS = {
        new Column(SegmentCostTable.COLUMN_ID, new NonOperation()),
        new Column(SegmentCostTable.COLUMN_SEGMENT_ID, new CoverOperation()),
        new Column(SegmentCostTable.COLUMN_SERVICE_NAME, new CoverOperation()),
    };

    private static final Column[] LONG_COLUMNS = {
        new Column(SegmentCostTable.COLUMN_COST, new CoverOperation()),
        new Column(SegmentCostTable.COLUMN_START_TIME, new CoverOperation()),
        new Column(SegmentCostTable.COLUMN_END_TIME, new CoverOperation()),
        new Column(SegmentCostTable.COLUMN_TIME_BUCKET, new CoverOperation()),
    };

    private static final Column[] DOUBLE_COLUMNS = {};

    private static final Column[] INTEGER_COLUMNS = {
        new Column(SegmentCostTable.COLUMN_APPLICATION_ID, new CoverOperation()),
        new Column(SegmentCostTable.COLUMN_IS_ERROR, new CoverOperation()),
    };

    private static final Column[] BYTE_COLUMNS = {};

    public SegmentCost() {
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

    public Long getCost() {
        return getDataLong(0);
    }

    public void setCost(Long cost) {
        setDataLong(0, cost);
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
