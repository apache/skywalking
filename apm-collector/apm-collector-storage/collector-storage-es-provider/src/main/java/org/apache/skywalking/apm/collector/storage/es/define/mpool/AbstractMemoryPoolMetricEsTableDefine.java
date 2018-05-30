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

package org.apache.skywalking.apm.collector.storage.es.define.mpool;

import org.apache.skywalking.apm.collector.storage.es.base.define.ElasticSearchColumnDefine;
import org.apache.skywalking.apm.collector.storage.es.base.define.ElasticSearchTableDefine;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetricTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractMemoryPoolMetricEsTableDefine extends ElasticSearchTableDefine {

    AbstractMemoryPoolMetricEsTableDefine(String name) {
        super(name);
    }

    @Override public int refreshInterval() {
        return 1;
    }

    @Override public final void initialize() {
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.ID, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.METRIC_ID, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.INSTANCE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.POOL_TYPE, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.INIT, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.MAX, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.USED, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.COMMITTED, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.TIMES, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(MemoryPoolMetricTable.TIME_BUCKET, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
