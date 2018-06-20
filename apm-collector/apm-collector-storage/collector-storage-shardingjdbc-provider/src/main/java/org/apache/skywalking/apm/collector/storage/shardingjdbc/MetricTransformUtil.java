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

package org.apache.skywalking.apm.collector.storage.shardingjdbc;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.apache.skywalking.apm.collector.storage.table.Metric;
import org.apache.skywalking.apm.collector.storage.table.MetricColumns;

/**
 * @author linjiaqi
 */
public enum MetricTransformUtil {
    INSTANCE;

    public void shardingjdbcDataToStreamData(ResultSet source, Metric target) throws SQLException {
        target.setSourceValue(source.getInt(MetricColumns.SOURCE_VALUE.getName()));
        target.setTimeBucket(source.getLong(MetricColumns.TIME_BUCKET.getName()));

        target.setTransactionCalls(source.getLong(MetricColumns.TRANSACTION_CALLS.getName()));
        target.setTransactionErrorCalls(source.getLong(MetricColumns.TRANSACTION_ERROR_CALLS.getName()));
        target.setTransactionDurationSum(source.getLong(MetricColumns.TRANSACTION_DURATION_SUM.getName()));
        target.setTransactionErrorDurationSum(source.getLong(MetricColumns.TRANSACTION_ERROR_DURATION_SUM.getName()));
        target.setTransactionAverageDuration(source.getLong(MetricColumns.TRANSACTION_AVERAGE_DURATION.getName()));

        target.setBusinessTransactionCalls(source.getLong(MetricColumns.BUSINESS_TRANSACTION_CALLS.getName()));
        target.setBusinessTransactionErrorCalls(source.getLong(MetricColumns.BUSINESS_TRANSACTION_ERROR_CALLS.getName()));
        target.setBusinessTransactionDurationSum(source.getLong(MetricColumns.BUSINESS_TRANSACTION_DURATION_SUM.getName()));
        target.setBusinessTransactionErrorDurationSum(source.getLong(MetricColumns.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName()));
        target.setBusinessTransactionAverageDuration(source.getLong(MetricColumns.BUSINESS_TRANSACTION_AVERAGE_DURATION.getName()));

        target.setMqTransactionCalls(source.getLong(MetricColumns.MQ_TRANSACTION_CALLS.getName()));
        target.setMqTransactionErrorCalls(source.getLong(MetricColumns.MQ_TRANSACTION_ERROR_CALLS.getName()));
        target.setMqTransactionDurationSum(source.getLong(MetricColumns.MQ_TRANSACTION_DURATION_SUM.getName()));
        target.setMqTransactionErrorDurationSum(source.getLong(MetricColumns.MQ_TRANSACTION_ERROR_DURATION_SUM.getName()));
        target.setMqTransactionAverageDuration(source.getLong(MetricColumns.MQ_TRANSACTION_AVERAGE_DURATION.getName()));
    }

    public void streamDataToShardingjdbcData(Metric source, Map<String, Object> target) {
        target.put(MetricColumns.SOURCE_VALUE.getName(), source.getSourceValue());
        target.put(MetricColumns.TIME_BUCKET.getName(), source.getTimeBucket());

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
