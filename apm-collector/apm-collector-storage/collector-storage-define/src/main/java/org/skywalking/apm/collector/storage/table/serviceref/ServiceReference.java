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

package org.skywalking.apm.collector.storage.table.serviceref;

import org.skywalking.apm.collector.core.data.Column;
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.core.data.operator.AddOperation;
import org.skywalking.apm.collector.core.data.operator.CoverOperation;
import org.skywalking.apm.collector.core.data.operator.NonOperation;

/**
 * @author peng-yongsheng
 */
public class ServiceReference extends Data {

    private static final Column[] STRING_COLUMNS = {
        new Column(ServiceReferenceTable.COLUMN_ID, new NonOperation()),
        new Column(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, new NonOperation()),
        new Column(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, new NonOperation()),
        new Column(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME, new NonOperation()),
    };

    private static final Column[] LONG_COLUMNS = {
        new Column(ServiceReferenceTable.COLUMN_S1_LTE, new AddOperation()),
        new Column(ServiceReferenceTable.COLUMN_S3_LTE, new AddOperation()),
        new Column(ServiceReferenceTable.COLUMN_S5_LTE, new AddOperation()),
        new Column(ServiceReferenceTable.COLUMN_S5_GT, new AddOperation()),
        new Column(ServiceReferenceTable.COLUMN_SUMMARY, new AddOperation()),
        new Column(ServiceReferenceTable.COLUMN_ERROR, new AddOperation()),
        new Column(ServiceReferenceTable.COLUMN_COST_SUMMARY, new AddOperation()),
        new Column(ServiceReferenceTable.COLUMN_TIME_BUCKET, new CoverOperation()),
    };
    private static final Column[] DOUBLE_COLUMNS = {};
    private static final Column[] INTEGER_COLUMNS = {
        new Column(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, new NonOperation()),
        new Column(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, new NonOperation()),
        new Column(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID, new NonOperation()),
    };

    private static final Column[] BOOLEAN_COLUMNS = {};
    private static final Column[] BYTE_COLUMNS = {};

    public ServiceReference(String id) {
        super(id, STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BOOLEAN_COLUMNS, BYTE_COLUMNS);
        setS1Lte(0L);
        setS3Lte(0L);
        setS5Lte(0L);
        setS5Gt(0L);
        setError(0L);
        setSummary(0L);
        setCostSummary(0L);
    }

    public String getEntryServiceName() {
        return getDataString(1);
    }

    public void setEntryServiceName(String entryServiceName) {
        setDataString(1, entryServiceName);
    }

    public String getFrontServiceName() {
        return getDataString(2);
    }

    public void setFrontServiceName(String frontServiceName) {
        setDataString(2, frontServiceName);
    }

    public String getBehindServiceName() {
        return getDataString(3);
    }

    public void setBehindServiceName(String behindServiceName) {
        setDataString(3, behindServiceName);
    }

    public Integer getEntryServiceId() {
        return getDataInteger(0);
    }

    public void setEntryServiceId(Integer entryServiceId) {
        setDataInteger(0, entryServiceId);
    }

    public Integer getFrontServiceId() {
        return getDataInteger(1);
    }

    public void setFrontServiceId(Integer frontServiceId) {
        setDataInteger(1, frontServiceId);
    }

    public Integer getBehindServiceId() {
        return getDataInteger(2);
    }

    public void setBehindServiceId(Integer behindServiceId) {
        setDataInteger(2, behindServiceId);
    }

    public Long getS1Lte() {
        return getDataLong(0);
    }

    public void setS1Lte(Long s1Lte) {
        setDataLong(0, s1Lte);
    }

    public Long getS3Lte() {
        return getDataLong(1);
    }

    public void setS3Lte(Long s3Lte) {
        setDataLong(1, s3Lte);
    }

    public Long getS5Lte() {
        return getDataLong(2);
    }

    public void setS5Lte(Long s5Lte) {
        setDataLong(2, s5Lte);
    }

    public Long getS5Gt() {
        return getDataLong(3);
    }

    public void setS5Gt(Long s5Gt) {
        setDataLong(3, s5Gt);
    }

    public Long getSummary() {
        return getDataLong(4);
    }

    public void setSummary(Long summary) {
        setDataLong(4, summary);
    }

    public Long getError() {
        return getDataLong(5);
    }

    public void setError(Long error) {
        setDataLong(5, error);
    }

    public Long getCostSummary() {
        return getDataLong(6);
    }

    public void setCostSummary(Long costSummary) {
        setDataLong(6, costSummary);
    }

    public Long getTimeBucket() {
        return getDataLong(7);
    }

    public void setTimeBucket(Long timeBucket) {
        setDataLong(7, timeBucket);
    }
}
