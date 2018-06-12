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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.define.irmp;

import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcColumnDefine;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcTableDefine;
import org.apache.skywalking.apm.collector.storage.table.instance.InstanceReferenceMetricTable;

/**
 * @author linjiaqi
 */
public abstract class AbstractInstanceReferenceMetricShardingjdbcTableDefine extends ShardingjdbcTableDefine {

    AbstractInstanceReferenceMetricShardingjdbcTableDefine(String name) {
        super(name, InstanceReferenceMetricTable.TIME_BUCKET.getName());
    }

    @Override public final void initialize() {
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.ID, ShardingjdbcColumnDefine.Type.Varchar.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.METRIC_ID, ShardingjdbcColumnDefine.Type.Varchar.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.FRONT_APPLICATION_ID, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.FRONT_INSTANCE_ID, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.BEHIND_APPLICATION_ID, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.BEHIND_INSTANCE_ID, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.SOURCE_VALUE, ShardingjdbcColumnDefine.Type.Int.name()));

        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.TRANSACTION_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.TRANSACTION_ERROR_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.TRANSACTION_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.TRANSACTION_ERROR_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.TRANSACTION_AVERAGE_DURATION, ShardingjdbcColumnDefine.Type.Bigint.name()));

        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION, ShardingjdbcColumnDefine.Type.Bigint.name()));

        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.MQ_TRANSACTION_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.MQ_TRANSACTION_ERROR_CALLS, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.MQ_TRANSACTION_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION, ShardingjdbcColumnDefine.Type.Bigint.name()));

        addColumn(new ShardingjdbcColumnDefine(InstanceReferenceMetricTable.TIME_BUCKET, ShardingjdbcColumnDefine.Type.Bigint.name()));
    }
}
