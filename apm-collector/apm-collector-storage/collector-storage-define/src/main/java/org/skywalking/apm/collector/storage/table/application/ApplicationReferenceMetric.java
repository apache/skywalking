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
        new Column(ApplicationReferenceMetricTable.COLUMN_TIME_BUCKET, new NonOperation()),
    };
    private static final Column[] DOUBLE_COLUMNS = {};
    private static final Column[] INTEGER_COLUMNS = {
        new Column(ApplicationReferenceMetricTable.COLUMN_FRONT_APPLICATION_ID, new NonOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_BEHIND_APPLICATION_ID, new NonOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_S1_LTE, new AddOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_S3_LTE, new AddOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_S5_LTE, new AddOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_S5_GT, new AddOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_SUMMARY, new AddOperation()),
        new Column(ApplicationReferenceMetricTable.COLUMN_ERROR, new AddOperation()),
    };

    private static final Column[] BOOLEAN_COLUMNS = {};
    private static final Column[] BYTE_COLUMNS = {};

    public ApplicationReferenceMetric(String id) {
        super(id, STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BOOLEAN_COLUMNS, BYTE_COLUMNS);
        setS1Lte(0);
        setS3Lte(0);
        setS5Lte(0);
        setS5Gt(0);
        setError(0);
        setSummary(0);
    }

    public Long getTimeBucket() {
        return getDataLong(0);
    }

    public void setTimeBucket(Long timeBucket) {
        setDataLong(0, timeBucket);
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

    public Integer getS1Lte() {
        return getDataInteger(2);
    }

    public void setS1Lte(Integer s1Lte) {
        setDataInteger(2, s1Lte);
    }

    public Integer getS3Lte() {
        return getDataInteger(3);
    }

    public void setS3Lte(Integer s3Lte) {
        setDataInteger(3, s3Lte);
    }

    public Integer getS5Lte() {
        return getDataInteger(4);
    }

    public void setS5Lte(Integer s5Lte) {
        setDataInteger(4, s5Lte);
    }

    public Integer getS5Gt() {
        return getDataInteger(5);
    }

    public void setS5Gt(Integer s5Gt) {
        setDataInteger(5, s5Gt);
    }

    public Integer getSummary() {
        return getDataInteger(6);
    }

    public void setSummary(Integer summary) {
        setDataInteger(6, summary);
    }

    public Integer getError() {
        return getDataInteger(7);
    }

    public void setError(Integer error) {
        setDataInteger(7, error);
    }
}
