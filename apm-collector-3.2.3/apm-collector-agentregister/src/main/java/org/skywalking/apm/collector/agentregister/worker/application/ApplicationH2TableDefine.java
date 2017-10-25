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

package org.skywalking.apm.collector.agentregister.worker.application;

import org.skywalking.apm.collector.storage.define.global.GlobalTraceTable;
import org.skywalking.apm.collector.storage.define.register.ApplicationTable;
import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;

/**
 * @author peng-yongsheng
 */
public class ApplicationH2TableDefine extends H2TableDefine {

    public ApplicationH2TableDefine() {
        super(ApplicationTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(GlobalTraceTable.COLUMN_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(ApplicationTable.COLUMN_APPLICATION_CODE, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(ApplicationTable.COLUMN_APPLICATION_ID, H2ColumnDefine.Type.Int.name()));
    }
}
