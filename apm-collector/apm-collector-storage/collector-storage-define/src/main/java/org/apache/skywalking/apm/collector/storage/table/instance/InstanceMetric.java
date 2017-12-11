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


package org.apache.skywalking.apm.collector.storage.table.instance;

import org.apache.skywalking.apm.collector.core.data.Column;
import org.apache.skywalking.apm.collector.core.data.Data;
import org.apache.skywalking.apm.collector.core.data.operator.AddOperation;
import org.apache.skywalking.apm.collector.core.data.operator.CoverOperation;
import org.apache.skywalking.apm.collector.core.data.operator.NonOperation;

/**
 * @author peng-yongsheng
 */
public class InstanceMetric extends Data {

    private static final Column[] STRING_COLUMNS = {
        new Column(InstanceMetricTable.COLUMN_ID, new NonOperation()),
    };

    private static final Column[] LONG_COLUMNS = {
        new Column(InstanceMetricTable.COLUMN_TIME_BUCKET, new NonOperation()),

        new Column(InstanceMetricTable.COLUMN_TRANSACTION_CALLS, new AddOperation()),
        new Column(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_CALLS, new AddOperation()),
        new Column(InstanceMetricTable.COLUMN_TRANSACTION_DURATION_SUM, new AddOperation()),
        new Column(InstanceMetricTable.COLUMN_TRANSACTION_ERROR_DURATION_SUM, new AddOperation()),
        new Column(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_CALLS, new AddOperation()),
        new Column(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS, new AddOperation()),
        new Column(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_DURATION_SUM, new AddOperation()),
        new Column(InstanceMetricTable.COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM, new AddOperation()),
        new Column(InstanceMetricTable.COLUMN_MQ_TRANSACTION_CALLS, new AddOperation()),
        new Column(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_CALLS, new AddOperation()),
        new Column(InstanceMetricTable.COLUMN_MQ_TRANSACTION_DURATION_SUM, new AddOperation()),
        new Column(InstanceMetricTable.COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM, new AddOperation()),
    };

    private static final Column[] DOUBLE_COLUMNS = {};

    private static final Column[] INTEGER_COLUMNS = {
        new Column(InstanceMetricTable.COLUMN_APPLICATION_ID, new CoverOperation()),
        new Column(InstanceMetricTable.COLUMN_INSTANCE_ID, new CoverOperation()),
    };

    private static final Column[] BOOLEAN_COLUMNS = {};
    private static final Column[] BYTE_COLUMNS = {};

    public InstanceMetric(String id) {
        super(id, STRING_COLUMNS, LONG_COLUMNS, DOUBLE_COLUMNS, INTEGER_COLUMNS, BOOLEAN_COLUMNS, BYTE_COLUMNS);
    }

    public Integer getApplicationId() {
        return getDataInteger(0);
    }

    public void setApplicationId(Integer applicationId) {
        setDataInteger(0, applicationId);
    }

    public Integer getInstanceId() {
        return getDataInteger(1);
    }

    public void setInstanceId(Integer instanceId) {
        setDataInteger(1, instanceId);
    }

    public Long getTimeBucket() {
        return getDataLong(0);
    }

    public void setTimeBucket(Long timeBucket) {
        setDataLong(0, timeBucket);
    }

    public Long getTransactionCalls() {
        return getDataLong(1);
    }

    public void setTransactionCalls(Long transactionCalls) {
        setDataLong(1, transactionCalls);
    }

    public Long getTransactionErrorCalls() {
        return getDataLong(2);
    }

    public void setTransactionErrorCalls(Long transactionErrorCalls) {
        setDataLong(2, transactionErrorCalls);
    }

    public Long getTransactionDurationSum() {
        return getDataLong(3);
    }

    public void setTransactionDurationSum(Long transactionDurationSum) {
        setDataLong(3, transactionDurationSum);
    }

    public Long getTransactionErrorDurationSum() {
        return getDataLong(4);
    }

    public void setTransactionErrorDurationSum(Long transactionErrorDurationSum) {
        setDataLong(4, transactionErrorDurationSum);
    }

    public Long getBusinessTransactionCalls() {
        return getDataLong(5);
    }

    public void setBusinessTransactionCalls(Long businessTransactionCalls) {
        setDataLong(5, businessTransactionCalls);
    }

    public Long getBusinessTransactionErrorCalls() {
        return getDataLong(6);
    }

    public void setBusinessTransactionErrorCalls(Long businessTransactionErrorCalls) {
        setDataLong(6, businessTransactionErrorCalls);
    }

    public Long getBusinessTransactionDurationSum() {
        return getDataLong(7);
    }

    public void setBusinessTransactionDurationSum(Long businessTransactionDurationSum) {
        setDataLong(7, businessTransactionDurationSum);
    }

    public Long getBusinessTransactionErrorDurationSum() {
        return getDataLong(8);
    }

    public void setBusinessTransactionErrorDurationSum(Long businessTransactionErrorDurationSum) {
        setDataLong(8, businessTransactionErrorDurationSum);
    }

    public Long getMqTransactionCalls() {
        return getDataLong(9);
    }

    public void setMqTransactionCalls(Long mqTransactionCalls) {
        setDataLong(9, mqTransactionCalls);
    }

    public Long getMqTransactionErrorCalls() {
        return getDataLong(10);
    }

    public void setMqTransactionErrorCalls(Long mqTransactionErrorCalls) {
        setDataLong(10, mqTransactionErrorCalls);
    }

    public Long getMqTransactionDurationSum() {
        return getDataLong(11);
    }

    public void setMqTransactionDurationSum(Long mqTransactionDurationSum) {
        setDataLong(11, mqTransactionDurationSum);
    }

    public Long getMqTransactionErrorDurationSum() {
        return getDataLong(12);
    }

    public void setMqTransactionErrorDurationSum(Long mqTransactionErrorDurationSum) {
        setDataLong(12, mqTransactionErrorDurationSum);
    }
}
