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

package org.apache.skywalking.apm.collector.storage.table.application;

import org.apache.skywalking.apm.collector.core.data.*;
import org.apache.skywalking.apm.collector.core.data.column.*;
import org.apache.skywalking.apm.collector.core.data.operator.*;
import org.apache.skywalking.apm.collector.remote.service.RemoteDataRegisterService;
import org.apache.skywalking.apm.collector.storage.table.Metric;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceMetric extends StreamData implements Metric {

    private static final StringColumn[] STRING_COLUMNS = {
        new StringColumn(ApplicationReferenceMetricTable.ID, new NonMergeOperation()),
        new StringColumn(ApplicationReferenceMetricTable.METRIC_ID, new NonMergeOperation()),
    };

    private static final LongColumn[] LONG_COLUMNS = {
        new LongColumn(ApplicationReferenceMetricTable.TIME_BUCKET, new NonMergeOperation()),

        new LongColumn(ApplicationReferenceMetricTable.TRANSACTION_CALLS, new AddMergeOperation()),
        new LongColumn(ApplicationReferenceMetricTable.TRANSACTION_ERROR_CALLS, new AddMergeOperation()),
        new LongColumn(ApplicationReferenceMetricTable.TRANSACTION_DURATION_SUM, new AddMergeOperation()),
        new LongColumn(ApplicationReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM, new AddMergeOperation()),
        new LongColumn(ApplicationReferenceMetricTable.TRANSACTION_AVERAGE_DURATION, new NonMergeOperation(), new TransactionAverageDurationFormulaOperation()),
        new LongColumn(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_CALLS, new AddMergeOperation()),
        new LongColumn(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS, new AddMergeOperation()),
        new LongColumn(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM, new AddMergeOperation()),
        new LongColumn(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM, new AddMergeOperation()),
        new LongColumn(ApplicationReferenceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION, new NonMergeOperation(), new BusinessTransactionAverageDurationFormulaOperation()),
        new LongColumn(ApplicationReferenceMetricTable.MQ_TRANSACTION_CALLS, new AddMergeOperation()),
        new LongColumn(ApplicationReferenceMetricTable.MQ_TRANSACTION_ERROR_CALLS, new AddMergeOperation()),
        new LongColumn(ApplicationReferenceMetricTable.MQ_TRANSACTION_DURATION_SUM, new AddMergeOperation()),
        new LongColumn(ApplicationReferenceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM, new AddMergeOperation()),
        new LongColumn(ApplicationReferenceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION, new NonMergeOperation(), new MqTransactionAverageDurationFormulaOperation()),

        new LongColumn(ApplicationReferenceMetricTable.SATISFIED_COUNT, new AddMergeOperation()),
        new LongColumn(ApplicationReferenceMetricTable.TOLERATING_COUNT, new AddMergeOperation()),
        new LongColumn(ApplicationReferenceMetricTable.FRUSTRATED_COUNT, new AddMergeOperation()),
    };

    private static final IntegerColumn[] INTEGER_COLUMNS = {
        new IntegerColumn(ApplicationReferenceMetricTable.SOURCE_VALUE, new NonMergeOperation()),
        new IntegerColumn(ApplicationReferenceMetricTable.FRONT_APPLICATION_ID, new NonMergeOperation()),
        new IntegerColumn(ApplicationReferenceMetricTable.BEHIND_APPLICATION_ID, new NonMergeOperation()),
    };

    private static final DoubleColumn[] DOUBLE_COLUMNS = {
    };

    public ApplicationReferenceMetric() {
        super(STRING_COLUMNS, LONG_COLUMNS, INTEGER_COLUMNS, DOUBLE_COLUMNS);
    }

    @Override public String getId() {
        return getDataString(0);
    }

    @Override public void setId(String id) {
        setDataString(0, id);
    }

    @Override public String getMetricId() {
        return getDataString(1);
    }

    @Override public void setMetricId(String metricId) {
        setDataString(1, metricId);
    }

    @Override
    public Integer getSourceValue() {
        return getDataInteger(0);
    }

    @Override
    public void setSourceValue(Integer sourceValue) {
        setDataInteger(0, sourceValue);
    }

    public Integer getFrontApplicationId() {
        return getDataInteger(1);
    }

    public void setFrontApplicationId(Integer frontApplicationId) {
        setDataInteger(1, frontApplicationId);
    }

    public Integer getBehindApplicationId() {
        return getDataInteger(2);
    }

    public void setBehindApplicationId(Integer behindApplicationId) {
        setDataInteger(2, behindApplicationId);
    }

    @Override
    public Long getTimeBucket() {
        return getDataLong(0);
    }

    @Override
    public void setTimeBucket(Long timeBucket) {
        setDataLong(0, timeBucket);
    }

    @Override
    public Long getTransactionCalls() {
        return getDataLong(1);
    }

    @Override
    public void setTransactionCalls(Long transactionCalls) {
        setDataLong(1, transactionCalls);
    }

    @Override
    public Long getTransactionErrorCalls() {
        return getDataLong(2);
    }

    @Override
    public void setTransactionErrorCalls(Long transactionErrorCalls) {
        setDataLong(2, transactionErrorCalls);
    }

    @Override
    public Long getTransactionDurationSum() {
        return getDataLong(3);
    }

    @Override
    public void setTransactionDurationSum(Long transactionDurationSum) {
        setDataLong(3, transactionDurationSum);
    }

    @Override
    public Long getTransactionErrorDurationSum() {
        return getDataLong(4);
    }

    @Override
    public void setTransactionErrorDurationSum(Long transactionErrorDurationSum) {
        setDataLong(4, transactionErrorDurationSum);
    }

    @Override public Long getTransactionAverageDuration() {
        return getDataLong(5);
    }

    @Override public void setTransactionAverageDuration(Long transactionAverageDuration) {
        setDataLong(5, transactionAverageDuration);
    }

    @Override
    public Long getBusinessTransactionCalls() {
        return getDataLong(6);
    }

    @Override
    public void setBusinessTransactionCalls(Long businessTransactionCalls) {
        setDataLong(6, businessTransactionCalls);
    }

    @Override
    public Long getBusinessTransactionErrorCalls() {
        return getDataLong(7);
    }

    @Override
    public void setBusinessTransactionErrorCalls(Long businessTransactionErrorCalls) {
        setDataLong(7, businessTransactionErrorCalls);
    }

    @Override
    public Long getBusinessTransactionDurationSum() {
        return getDataLong(8);
    }

    @Override
    public void setBusinessTransactionDurationSum(Long businessTransactionDurationSum) {
        setDataLong(8, businessTransactionDurationSum);
    }

    @Override
    public Long getBusinessTransactionErrorDurationSum() {
        return getDataLong(9);
    }

    @Override
    public void setBusinessTransactionErrorDurationSum(Long businessTransactionErrorDurationSum) {
        setDataLong(9, businessTransactionErrorDurationSum);
    }

    @Override public Long getBusinessTransactionAverageDuration() {
        return getDataLong(10);
    }

    @Override public void setBusinessTransactionAverageDuration(Long businessTransactionAverageDuration) {
        setDataLong(10, businessTransactionAverageDuration);
    }

    @Override
    public Long getMqTransactionCalls() {
        return getDataLong(11);
    }

    @Override
    public void setMqTransactionCalls(Long mqTransactionCalls) {
        setDataLong(11, mqTransactionCalls);
    }

    @Override
    public Long getMqTransactionErrorCalls() {
        return getDataLong(12);
    }

    @Override
    public void setMqTransactionErrorCalls(Long mqTransactionErrorCalls) {
        setDataLong(12, mqTransactionErrorCalls);
    }

    @Override
    public Long getMqTransactionDurationSum() {
        return getDataLong(13);
    }

    @Override
    public void setMqTransactionDurationSum(Long mqTransactionDurationSum) {
        setDataLong(13, mqTransactionDurationSum);
    }

    @Override
    public Long getMqTransactionErrorDurationSum() {
        return getDataLong(14);
    }

    @Override
    public void setMqTransactionErrorDurationSum(Long mqTransactionErrorDurationSum) {
        setDataLong(14, mqTransactionErrorDurationSum);
    }

    @Override public Long getMqTransactionAverageDuration() {
        return getDataLong(15);
    }

    @Override public void setMqTransactionAverageDuration(Long mqTransactionAverageDuration) {
        setDataLong(15, mqTransactionAverageDuration);
    }

    public long getSatisfiedCount() {
        return getDataLong(16);
    }

    public void setSatisfiedCount(long satisfiedCount) {
        setDataLong(16, satisfiedCount);
    }

    public long getToleratingCount() {
        return getDataLong(17);
    }

    public void setToleratingCount(long toleratingCount) {
        setDataLong(17, toleratingCount);
    }

    public long getFrustratedCount() {
        return getDataLong(18);
    }

    public void setFrustratedCount(long frustratedCount) {
        setDataLong(18, frustratedCount);
    }

    public static class InstanceCreator implements RemoteDataRegisterService.RemoteDataInstanceCreator {
        @Override public RemoteData createInstance() {
            return new ApplicationReferenceMetric();
        }
    }

    private static class TransactionAverageDurationFormulaOperation implements FormulaOperation<ApplicationReferenceMetric, Long> {

        @Override public Long operate(ApplicationReferenceMetric data) {
            return data.getTransactionCalls() == 0 ? 0 : data.getTransactionDurationSum() / data.getTransactionCalls();
        }
    }

    private static class BusinessTransactionAverageDurationFormulaOperation implements FormulaOperation<ApplicationReferenceMetric, Long> {

        @Override public Long operate(ApplicationReferenceMetric data) {
            return data.getBusinessTransactionCalls() == 0 ? 0 : data.getBusinessTransactionDurationSum() / data.getBusinessTransactionCalls();
        }
    }

    private static class MqTransactionAverageDurationFormulaOperation implements FormulaOperation<ApplicationReferenceMetric, Long> {

        @Override public Long operate(ApplicationReferenceMetric data) {
            return data.getMqTransactionCalls() == 0 ? 0 : data.getMqTransactionDurationSum() / data.getMqTransactionCalls();
        }
    }
}
