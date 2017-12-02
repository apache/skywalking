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

package org.skywalking.apm.collector.storage.table.service;

import org.skywalking.apm.collector.core.data.Column;
import org.skywalking.apm.collector.core.data.Data;
import org.skywalking.apm.collector.core.data.operator.AddOperation;
import org.skywalking.apm.collector.core.data.operator.CoverOperation;
import org.skywalking.apm.collector.core.data.operator.NonOperation;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceMetric extends Data {

    private static final Column[] STRING_COLUMNS = {
        new Column(ServiceReferenceMetricTable.COLUMN_ID, new NonOperation()),
    };

    private static final Column[] LONG_COLUMNS = {
        new Column(ServiceReferenceMetricTable.COLUMN_TRANSACTION_CALLS, new AddOperation()),
        new Column(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, new AddOperation()),
        new Column(ServiceReferenceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, new AddOperation()),
        new Column(ServiceReferenceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, new AddOperation()),
        new Column(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, new AddOperation()),
        new Column(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, new AddOperation()),
        new Column(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, new AddOperation()),
        new Column(ServiceReferenceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, new AddOperation()),
        new Column(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, new AddOperation()),
        new Column(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, new AddOperation()),
        new Column(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, new AddOperation()),
        new Column(ServiceReferenceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, new AddOperation()),
        new Column(ServiceReferenceMetricTable.COLUMN_TIME_BUCKET, new CoverOperation()),
    };

    private static final Column[] DOUBLE_COLUMNS = {};

    private static final Column[] INTEGER_COLUMNS = {
        new Column(ServiceReferenceMetricTable.COLUMN_ENTRY_SERVICE_ID, new NonOperation()),
        new Column(ServiceReferenceMetricTable.COLUMN_FRONT_SERVICE_ID, new NonOperation()),
        new Column(ServiceReferenceMetricTable.COLUMN_BEHIND_SERVICE_ID, new NonOperation()),
    };

    private static final Column[] BOOLEAN_COLUMNS = {};

    private static final Column[] BYTE_COLUMNS = {};

    public ServiceReferenceMetric(String id) {
        super(id, STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BOOLEAN_COLUMNS, BYTE_COLUMNS);
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

    public Long getTransactionCalls() {
        return getDataLong(0);
    }

    public void setTransactionCalls(Long transactionCalls) {
        setDataLong(0, transactionCalls);
    }

    public Long getTransactionErrorCalls() {
        return getDataLong(1);
    }

    public void setTransactionErrorCalls(Long transactionErrorCalls) {
        setDataLong(1, transactionErrorCalls);
    }

    public Long getTransactionDurationSum() {
        return getDataLong(2);
    }

    public void setTransactionDurationSum(Long transactionDurationSum) {
        setDataLong(2, transactionDurationSum);
    }

    public Long getTransactionErrorDurationSum() {
        return getDataLong(3);
    }

    public void setTransactionErrorDurationSum(Long transactionErrorDurationSum) {
        setDataLong(3, transactionErrorDurationSum);
    }

    public Long getBusinessTransactionCalls() {
        return getDataLong(4);
    }

    public void setBusinessTransactionCalls(Long businessTransactionCalls) {
        setDataLong(4, businessTransactionCalls);
    }

    public Long getBusinessTransactionErrorCalls() {
        return getDataLong(5);
    }

    public void setBusinessTransactionErrorCalls(Long businessTransactionErrorCalls) {
        setDataLong(5, businessTransactionErrorCalls);
    }

    public Long getBusinessTransactionDurationSum() {
        return getDataLong(6);
    }

    public void setBusinessTransactionDurationSum(Long businessTransactionDurationSum) {
        setDataLong(6, businessTransactionDurationSum);
    }

    public Long getBusinessTransactionErrorDurationSum() {
        return getDataLong(7);
    }

    public void setBusinessTransactionErrorDurationSum(Long businessTransactionErrorDurationSum) {
        setDataLong(7, businessTransactionErrorDurationSum);
    }

    public Long getMqTransactionCalls() {
        return getDataLong(8);
    }

    public void setMqTransactionCalls(Long mqTransactionCalls) {
        setDataLong(8, mqTransactionCalls);
    }

    public Long getMqTransactionErrorCalls() {
        return getDataLong(9);
    }

    public void setMqTransactionErrorCalls(Long mqTransactionErrorCalls) {
        setDataLong(9, mqTransactionErrorCalls);
    }

    public Long getMqTransactionDurationSum() {
        return getDataLong(10);
    }

    public void setMqTransactionDurationSum(Long mqTransactionDurationSum) {
        setDataLong(10, mqTransactionDurationSum);
    }

    public Long getMqTransactionErrorDurationSum() {
        return getDataLong(11);
    }

    public void setMqTransactionErrorDurationSum(Long mqTransactionErrorDurationSum) {
        setDataLong(11, mqTransactionErrorDurationSum);
    }

    public Long getTimeBucket() {
        return getDataLong(12);
    }

    public void setTimeBucket(Long timeBucket) {
        setDataLong(12, timeBucket);
    }
}
