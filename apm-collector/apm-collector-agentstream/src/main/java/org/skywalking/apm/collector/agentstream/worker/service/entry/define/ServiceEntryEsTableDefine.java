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

package org.skywalking.apm.collector.agentstream.worker.service.entry.define;

import org.skywalking.apm.collector.storage.define.service.ServiceEntryTable;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;

/**
 * @author peng-yongsheng
 */
public class ServiceEntryEsTableDefine extends ElasticSearchTableDefine {

    public ServiceEntryEsTableDefine() {
        super(ServiceEntryTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 2;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(ServiceEntryTable.COLUMN_APPLICATION_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME, ElasticSearchColumnDefine.Type.Text.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceEntryTable.COLUMN_REGISTER_TIME, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceEntryTable.COLUMN_NEWEST_TIME, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
