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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.define.amp;

import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcColumnDefine;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcTableDefine;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationMetricTable;

/**
 * @author linjiaqi
 */
public abstract class AbstractApplicationMetricShardingjdbcTableDefine extends ShardingjdbcTableDefine {

    AbstractApplicationMetricShardingjdbcTableDefine(String name) {
        super(name, ApplicationMetricTable.TIME_BUCKET.getName());
    }

    @Override public final void initialize() {
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.ID, ShardingjdbcColumnDefine.Type.Varchar.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.METRIC_ID, ShardingjdbcColumnDefine.Type.Varchar.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.APPLICATION_ID, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.SOURCE_VALUE, ShardingjdbcColumnDefine.Type.Int.name()));

        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.TRANSACTION_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.TRANSACTION_ERROR_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.TRANSACTION_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.TRANSACTION_ERROR_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.TRANSACTION_AVERAGE_DURATION, ShardingjdbcColumnDefine.Type.Bigint.name()));

        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.BUSINESS_TRANSACTION_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.BUSINESS_TRANSACTION_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION, ShardingjdbcColumnDefine.Type.Bigint.name()));

        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.MQ_TRANSACTION_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.MQ_TRANSACTION_ERROR_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.MQ_TRANSACTION_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.MQ_TRANSACTION_AVERAGE_DURATION, ShardingjdbcColumnDefine.Type.Bigint.name()));

        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.SATISFIED_COUNT, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.TOLERATING_COUNT, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.FRUSTRATED_COUNT, ShardingjdbcColumnDefine.Type.Bigint.name()));

        addColumn(new ShardingjdbcColumnDefine(ApplicationMetricTable.TIME_BUCKET, ShardingjdbcColumnDefine.Type.Bigint.name()));
    }
}
