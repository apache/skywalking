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

package org.apache.skywalking.apm.collector.storage.h2;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import org.apache.skywalking.apm.collector.storage.table.Metric;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetricTable;

/**
 * @author peng-yongsheng
 */
public enum MetricTransformUtil {
    INSTANCE;

    public void h2DataToStreamData(ResultSet source, Metric target) throws SQLException {
        target.setTransactionCalls(source.getLong(InstanceReferenceMetricTable.TRANSACTION_CALLS.getName()));
        target.setTransactionErrorCalls(source.getLong(InstanceReferenceMetricTable.TRANSACTION_ERROR_CALLS.getName()));
        target.setTransactionDurationSum(source.getLong(InstanceReferenceMetricTable.TRANSACTION_DURATION_SUM.getName()));
        target.setTransactionErrorDurationSum(source.getLong(InstanceReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM.getName()));

        target.setBusinessTransactionCalls(source.getLong(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_CALLS.getName()));
        target.setBusinessTransactionErrorCalls(source.getLong(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS.getName()));
        target.setBusinessTransactionDurationSum(source.getLong(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM.getName()));
        target.setBusinessTransactionErrorDurationSum(source.getLong(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM.getName()));

        target.setMqTransactionCalls(source.getLong(InstanceReferenceMetricTable.MQ_TRANSACTION_CALLS.getName()));
        target.setMqTransactionErrorCalls(source.getLong(InstanceReferenceMetricTable.MQ_TRANSACTION_ERROR_CALLS.getName()));
        target.setMqTransactionDurationSum(source.getLong(InstanceReferenceMetricTable.MQ_TRANSACTION_DURATION_SUM.getName()));
        target.setMqTransactionErrorDurationSum(source.getLong(InstanceReferenceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM.getName()));

    }

    public void streamDataToH2Data(Metric source, Map<String, Object> target) {
    }
}
