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

import org.apache.skywalking.apm.collector.core.data.CommonTable;

/**
 * @author peng-yongsheng
 */
public abstract class CommonMetricTable extends CommonTable {
    public static final String COLUMN_APPLICATION_ID = "application_id";
    public static final String COLUMN_INSTANCE_ID = "instance_id";
    public static final String COLUMN_SERVICE_ID = "service_id";
    public static final String COLUMN_TRANSACTION_CALLS = "transaction_calls";
    public static final String COLUMN_TRANSACTION_ERROR_CALLS = "transaction_error_calls";
    public static final String COLUMN_TRANSACTION_DURATION_SUM = "transaction_duration_sum";
    public static final String COLUMN_TRANSACTION_ERROR_DURATION_SUM = "transaction_error_duration_sum";
    public static final String COLUMN_TRANSACTION_AVERAGE_DURATION = "transaction_average_duration";
    public static final String COLUMN_BUSINESS_TRANSACTION_CALLS = "business_transaction_calls";
    public static final String COLUMN_BUSINESS_TRANSACTION_ERROR_CALLS = "business_transaction_error_calls";
    public static final String COLUMN_BUSINESS_TRANSACTION_DURATION_SUM = "business_transaction_duration_sum";
    public static final String COLUMN_BUSINESS_TRANSACTION_ERROR_DURATION_SUM = "business_transaction_error_duration_sum";
    public static final String COLUMN_BUSINESS_TRANSACTION_AVERAGE_DURATION = "business_transaction_average_duration";
    public static final String COLUMN_MQ_TRANSACTION_CALLS = "mq_transaction_calls";
    public static final String COLUMN_MQ_TRANSACTION_ERROR_CALLS = "mq_transaction_error_calls";
    public static final String COLUMN_MQ_TRANSACTION_DURATION_SUM = "mq_transaction_duration_sum";
    public static final String COLUMN_MQ_TRANSACTION_ERROR_DURATION_SUM = "mq_transaction_error_duration_sum";
    public static final String COLUMN_MQ_TRANSACTION_AVERAGE_DURATION = "mq_transaction_average_duration";
    public static final String COLUMN_SOURCE_VALUE = "source_value";
}
