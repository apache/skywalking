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

package org.apache.skywalking.apm.collector.storage.es;

import java.util.Map;
import org.apache.skywalking.apm.collector.storage.table.Metric;
import org.apache.skywalking.apm.collector.storage.table.MetricColumns;

/**
 * @author peng-yongsheng
 */
public enum MetricTransformUtil {
    INSTANCE;

    public void esDataToStreamData(Map<String, Object> source, Metric target) {
        target.setSourceValue(((Number)source.get(MetricColumns.SOURCE_VALUE.getName())).intValue());
        target.setTimeBucket(((Number)source.get(MetricColumns.TIME_BUCKET.getName())).longValue());

        target.setTransactionCalls(((Number)source.get(MetricColumns.TRANSACTION_CALLS.getName())).longValue());
        target.setTransactionErrorCalls(((Number)source.get(MetricColumns.TRANSACTION_ERROR_CALLS.getName())).longValue());
        target.setTransactionDurationSum(((Number)source.get(MetricColumns.TRANSACTION_DURATION_SUM.getName())).longValue());
        target.setTransactionErrorDurationSum(((Number)source.get(MetricColumns.TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        target.setTransactionAverageDuration(((Number)source.get(MetricColumns.TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        target.setBusinessTransactionCalls(((Number)source.get(MetricColumns.BUSINESS_TRANSACTION_CALLS.getName())).longValue());
        target.setBusinessTransactionErrorCalls(((Number)source.get(MetricColumns.BUSINESS_TRANSACTION_ERROR_CALLS.getName())).longValue());
        target.setBusinessTransactionDurationSum(((Number)source.get(MetricColumns.BUSINESS_TRANSACTION_DURATION_SUM.getName())).longValue());
        target.setBusinessTransactionErrorDurationSum(((Number)source.get(MetricColumns.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        target.setBusinessTransactionAverageDuration(((Number)source.get(MetricColumns.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName())).longValue());

        target.setMqTransactionCalls(((Number)source.get(MetricColumns.MQ_TRANSACTION_CALLS.getName())).longValue());
        target.setMqTransactionErrorCalls(((Number)source.get(MetricColumns.MQ_TRANSACTION_ERROR_CALLS.getName())).longValue());
        target.setMqTransactionDurationSum(((Number)source.get(MetricColumns.MQ_TRANSACTION_DURATION_SUM.getName())).longValue());
        target.setMqTransactionErrorDurationSum(((Number)source.get(MetricColumns.MQ_TRANSACTION_ERROR_DURATION_SUM.getName())).longValue());
        target.setMqTransactionAverageDuration(((Number)source.get(MetricColumns.MQ_TRANSACTION_AVERAGE_DURATION.getName())).longValue());
    }

    public void esStreamDataToEsData(Metric source, Map<String, Object> target) {
        target.put(MetricColumns.TIME_BUCKET.getName(), source.getTimeBucket());
        target.put(MetricColumns.SOURCE_VALUE.getName(), source.getSourceValue());

        target.put(MetricColumns.TRANSACTION_CALLS.getName(), source.getTransactionCalls());
        target.put(MetricColumns.TRANSACTION_ERROR_CALLS.getName(), source.getTransactionErrorCalls());
        target.put(MetricColumns.TRANSACTION_DURATION_SUM.getName(), source.getTransactionDurationSum());
        target.put(MetricColumns.TRANSACTION_ERROR_DURATION_SUM.getName(), source.getTransactionErrorDurationSum());
        target.put(MetricColumns.TRANSACTION_AVERAGE_DURATION.getName(), source.getTransactionAverageDuration());

        target.put(MetricColumns.BUSINESS_TRANSACTION_CALLS.getName(), source.getBusinessTransactionCalls());
        target.put(MetricColumns.BUSINESS_TRANSACTION_ERROR_CALLS.getName(), source.getBusinessTransactionErrorCalls());
        target.put(MetricColumns.BUSINESS_TRANSACTION_DURATION_SUM.getName(), source.getBusinessTransactionDurationSum());
        target.put(MetricColumns.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName(), source.getBusinessTransactionErrorDurationSum());
        target.put(MetricColumns.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName(), source.getBusinessTransactionAverageDuration());

        target.put(MetricColumns.MQ_TRANSACTION_CALLS.getName(), source.getMqTransactionCalls());
        target.put(MetricColumns.MQ_TRANSACTION_ERROR_CALLS.getName(), source.getMqTransactionErrorCalls());
        target.put(MetricColumns.MQ_TRANSACTION_DURATION_SUM.getName(), source.getMqTransactionDurationSum());
        target.put(MetricColumns.MQ_TRANSACTION_ERROR_DURATION_SUM.getName(), source.getMqTransactionErrorDurationSum());
        target.put(MetricColumns.MQ_TRANSACTION_AVERAGE_DURATION.getName(), source.getMqTransactionAverageDuration());
    }
}
