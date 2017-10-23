/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.agentjvm.worker.memory.define;

import org.skywalking.apm.collector.storage.define.jvm.MemoryMetricTable;
import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;

/**
 * @author peng-yongsheng
 */
public class MemoryMetricH2TableDefine extends H2TableDefine {

    public MemoryMetricH2TableDefine() {
        super(MemoryMetricTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_APPLICATION_INSTANCE_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_IS_HEAP, H2ColumnDefine.Type.Boolean.name()));
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_INIT, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_MAX, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_USED, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_COMMITTED, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
