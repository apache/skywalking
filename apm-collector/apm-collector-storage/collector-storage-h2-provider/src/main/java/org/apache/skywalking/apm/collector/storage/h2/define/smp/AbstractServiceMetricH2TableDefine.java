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

package org.apache.skywalking.apm.collector.storage.h2.define.smp;

import org.apache.skywalking.apm.collector.storage.h2.base.define.H2ColumnDefine;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2TableDefine;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractServiceMetricH2TableDefine extends H2TableDefine {

    AbstractServiceMetricH2TableDefine(String name) {
        super(name);
    }

    @Override public final void initialize() {
        addColumn(new H2ColumnDefine(ServiceMetricTable.ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.METRIC_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.APPLICATION_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.INSTANCE_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.SERVICE_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.SOURCE_VALUE, H2ColumnDefine.Type.Int.name()));

        addColumn(new H2ColumnDefine(ServiceMetricTable.TRANSACTION_CALLS, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.TRANSACTION_ERROR_CALLS, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.TRANSACTION_DURATION_SUM, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.TRANSACTION_ERROR_DURATION_SUM, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.TRANSACTION_AVERAGE_DURATION, H2ColumnDefine.Type.Bigint.name()));

        addColumn(new H2ColumnDefine(ServiceMetricTable.BUSINESS_TRANSACTION_CALLS, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION, H2ColumnDefine.Type.Bigint.name()));

        addColumn(new H2ColumnDefine(ServiceMetricTable.MQ_TRANSACTION_CALLS, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.MQ_TRANSACTION_ERROR_CALLS, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.MQ_TRANSACTION_DURATION_SUM, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION, H2ColumnDefine.Type.Bigint.name()));

        addColumn(new H2ColumnDefine(ServiceMetricTable.TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
