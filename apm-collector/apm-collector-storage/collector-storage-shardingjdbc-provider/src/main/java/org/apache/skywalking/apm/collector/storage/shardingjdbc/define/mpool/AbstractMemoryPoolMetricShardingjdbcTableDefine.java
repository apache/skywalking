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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.define.mpool;

import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcColumnDefine;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcTableDefine;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetricTable;

/**
 * @author linjiaqi
 */
public abstract class AbstractMemoryPoolMetricShardingjdbcTableDefine extends ShardingjdbcTableDefine {

    AbstractMemoryPoolMetricShardingjdbcTableDefine(String name) {
        super(name, MemoryPoolMetricTable.TIME_BUCKET.getName());
    }

    @Override public final void initialize() {
        addColumn(new ShardingjdbcColumnDefine(MemoryPoolMetricTable.ID, ShardingjdbcColumnDefine.Type.Varchar.name()));
        addColumn(new ShardingjdbcColumnDefine(MemoryPoolMetricTable.METRIC_ID, ShardingjdbcColumnDefine.Type.Varchar.name()));
        addColumn(new ShardingjdbcColumnDefine(MemoryPoolMetricTable.INSTANCE_ID, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(MemoryPoolMetricTable.POOL_TYPE, ShardingjdbcColumnDefine.Type.Int.name()));
        addColumn(new ShardingjdbcColumnDefine(MemoryPoolMetricTable.INIT, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(MemoryPoolMetricTable.MAX, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(MemoryPoolMetricTable.USED, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(MemoryPoolMetricTable.COMMITTED, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(MemoryPoolMetricTable.TIMES, ShardingjdbcColumnDefine.Type.Bigint.name()));
        addColumn(new ShardingjdbcColumnDefine(MemoryPoolMetricTable.TIME_BUCKET, ShardingjdbcColumnDefine.Type.Bigint.name()));
    }
}
