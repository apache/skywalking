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

package org.apache.skywalking.apm.collector.storage.table;

import org.apache.skywalking.apm.collector.core.data.ColumnName;
import org.apache.skywalking.apm.collector.core.data.CommonTable;

/**
 * @author peng-yongsheng
 */
public interface MetricColumns extends CommonTable {

    ColumnName TRANSACTION_CALLS = new ColumnName("transaction_calls", "t1");

    ColumnName TRANSACTION_ERROR_CALLS = new ColumnName("transaction_error_calls", "t2");

    ColumnName TRANSACTION_DURATION_SUM = new ColumnName("transaction_duration_sum", "t3");

    ColumnName TRANSACTION_ERROR_DURATION_SUM = new ColumnName("transaction_error_duration_sum", "t4");

    ColumnName TRANSACTION_AVERAGE_DURATION = new ColumnName("transaction_average_duration", "t5");

    ColumnName BUSINESS_TRANSACTION_CALLS = new ColumnName("business_transaction_calls", "b1");

    ColumnName BUSINESS_TRANSACTION_ERROR_CALLS = new ColumnName("business_transaction_error_calls", "b2");

    ColumnName BUSINESS_TRANSACTION_DURATION_SUM = new ColumnName("business_transaction_duration_sum", "b3");

    ColumnName BUSINESS_TRANSACTION_ERROR_DURATION_SUM = new ColumnName("business_transaction_error_duration_sum", "b4");

    ColumnName BUSINESS_TRANSACTION_AVERAGE_DURATION = new ColumnName("business_transaction_average_duration", "b5");

    ColumnName MQ_TRANSACTION_CALLS = new ColumnName("mq_transaction_calls", "m1");

    ColumnName MQ_TRANSACTION_ERROR_CALLS = new ColumnName("mq_transaction_error_calls", "m2");

    ColumnName MQ_TRANSACTION_DURATION_SUM = new ColumnName("mq_transaction_duration_sum", "m3");

    ColumnName MQ_TRANSACTION_ERROR_DURATION_SUM = new ColumnName("mq_transaction_error_duration_sum", "m4");

    ColumnName MQ_TRANSACTION_AVERAGE_DURATION = new ColumnName("mq_transaction_average_duration", "m5");

    ColumnName SATISFIED_COUNT = new ColumnName("satisfied_count", "a1");

    ColumnName TOLERATING_COUNT = new ColumnName("tolerating_count", "a2");

    ColumnName FRUSTRATED_COUNT = new ColumnName("frustrated_count", "a3");
}
