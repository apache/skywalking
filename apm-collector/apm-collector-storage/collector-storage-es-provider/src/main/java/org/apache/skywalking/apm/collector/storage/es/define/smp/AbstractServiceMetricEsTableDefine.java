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

package org.apache.skywalking.apm.collector.storage.es.define.smp;

import org.apache.skywalking.apm.collector.storage.es.base.define.ElasticSearchColumnDefine;
import org.apache.skywalking.apm.collector.storage.es.base.define.ElasticSearchTableDefine;
import org.apache.skywalking.apm.collector.storage.table.service.ServiceMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractServiceMetricEsTableDefine extends ElasticSearchTableDefine {

    AbstractServiceMetricEsTableDefine(String name) {
        super(name);
    }

    @Override public final void initialize() {
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.ID, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.METRIC_ID, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.APPLICATION_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.INSTANCE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.SERVICE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.SOURCE_VALUE, ElasticSearchColumnDefine.Type.Integer.name()));

        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.TRANSACTION_CALLS, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.TRANSACTION_ERROR_CALLS, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.TRANSACTION_DURATION_SUM, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.TRANSACTION_ERROR_DURATION_SUM, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.TRANSACTION_AVERAGE_DURATION, ElasticSearchColumnDefine.Type.Long.name()));

        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.BUSINESS_TRANSACTION_CALLS, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.BUSINESS_TRANSACTION_ERROR_CALLS, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.BUSINESS_TRANSACTION_DURATION_SUM, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.BUSINESS_TRANSACTION_ERROR_DURATION_SUM, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.BUSINESS_TRANSACTION_AVERAGE_DURATION, ElasticSearchColumnDefine.Type.Long.name()));

        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.MQ_TRANSACTION_CALLS, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.MQ_TRANSACTION_ERROR_CALLS, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.MQ_TRANSACTION_DURATION_SUM, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.MQ_TRANSACTION_ERROR_DURATION_SUM, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.MQ_TRANSACTION_AVERAGE_DURATION, ElasticSearchColumnDefine.Type.Long.name()));

        addColumn(new ElasticSearchColumnDefine(ServiceMetricTable.TIME_BUCKET, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
