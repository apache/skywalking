/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.storage.table.application;

import org.skywalking.apm.collector.core.data.Column;
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.core.data.operator.AddOperation;
import org.skywalking.apm.collector.core.data.operator.NonOperation;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceMetric extends Data {

    private static final Column[] STRING_COLUMNS = {
        new Column(ApplicationReferenceMetricTable.COLUMN_ID, new NonOperation()),
    };

    private static final Column[] LONG_COLUMNS = {
        new Column(ApplicationReferenceMetricTable.COLUMN_CALLS, new AddOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_ERROR_CALLS, new AddOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_DURATION_SUM, new AddOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_ERROR_DURATION_SUM, new AddOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_SATISFIED_COUNT, new AddOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_TOLERATING_COUNT, new AddOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_FRUSTRATED_COUNT, new AddOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET, new NonOperation()),
    };
    private static final Column[] DOUBLE_COLUMNS = {};
    private static final Column[] INTEGER_COLUMNS = {
        new Column(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID, new NonOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID, new NonOperation()),
    };

    private static final Column[] BOOLEAN_COLUMNS = {};
    private static final Column[] BYTE_COLUMNS = {};

    public ApplicationReferenceMetric(String id) {
        super(id, STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BOOLEAN_COLUMNS, BYTE_COLUMNS);
    }

    public Integer getFrontApplicationId() {
        return getDataInteger(0);
    }

    public void setFrontApplicationId(Integer frontApplicationId) {
        setDataInteger(0, frontApplicationId);
    }

    public Integer getBehindApplicationId() {
        return getDataInteger(1);
    }

    public void setBehindApplicationId(Integer behindApplicationId) {
        setDataInteger(1, behindApplicationId);
    }

    public Long getCalls() {
        return getDataLong(0);
    }

    public void setCalls(Long calls) {
        setDataLong(0, calls);
    }

    public Long getErrorCalls() {
        return getDataLong(1);
    }

    public void setErrorCalls(Long errorCalls) {
        setDataLong(1, errorCalls);
    }

    public Long getDurationSum() {
        return getDataLong(2);
    }

    public void setDurationSum(Long durationSum) {
        setDataLong(2, durationSum);
    }

    public Long getErrorDurationSum() {
        return getDataLong(3);
    }

    public void setErrorDurationSum(Long errorDurationSum) {
        setDataLong(3, errorDurationSum);
    }

    public long getSatisfiedCount() {
        return getDataLong(4);
    }

    public void setSatisfiedCount(long satisfiedCount) {
        setDataLong(4, satisfiedCount);
    }

    public long getToleratingCount() {
        return getDataLong(5);
    }

    public void setToleratingCount(long toleratingCount) {
        setDataLong(5, toleratingCount);
    }

    public long getFrustratedCount() {
        return getDataLong(6);
    }

    public void setFrustratedCount(long frustratedCount) {
        setDataLong(6, frustratedCount);
    }

    public Long getTimeBucket() {
        return getDataLong(7);
    }

    public void setTimeBucket(Long timeBucket) {
        setDataLong(7, timeBucket);
    }
}
