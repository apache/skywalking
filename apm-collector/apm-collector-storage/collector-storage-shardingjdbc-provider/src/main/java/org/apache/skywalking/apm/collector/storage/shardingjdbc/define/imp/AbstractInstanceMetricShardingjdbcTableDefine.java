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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.define.imp;

import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcColumnDefine;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcTableDefine;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceMetricTable;

/**
 * @author linjiaqi
 */
public abstract class AbstractInstanceMetricShardingjdbcTableDefine extends ShardingjdbcTableDefine {

    AbstractInstanceMetricShardingjdbcTableDefine(String name) {
        super(name, InstanceMetricTable.TIME_BUCKET.getName());
    }

    @Override public final void initialize() {
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.ID, ShardingjdbcColumnDefine.Type.Varchar.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.METRIC_ID, ShardingjdbcColumnDefine.Type.Varchar.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.APPLICATION_ID, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.INSTANCE_ID, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.SOURCE_VALUE, ShardingjdbcColumnDefine.Type.Int.name()));

        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.TRANSACTION_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.TRANSACTION_ERROR_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.TRANSACTION_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.TRANSACTION_ERROR_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.TRANSACTION_AVERAGE_DURATION, ShardingjdbcColumnDefine.Type.Bigint.name()));

        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.BUSINESS_TRANSACTION_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION, ShardingjdbcColumnDefine.Type.Bigint.name()));

        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.MQ_TRANSACTION_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.MQ_TRANSACTION_ERROR_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.MQ_TRANSACTION_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION, ShardingjdbcColumnDefine.Type.Bigint.name()));

        addColumn(new ShardingjdbcColumnDefine(InstanceMetricTable.TIME_BUCKET, ShardingjdbcColumnDefine.Type.Bigint.name()));
    }
}
