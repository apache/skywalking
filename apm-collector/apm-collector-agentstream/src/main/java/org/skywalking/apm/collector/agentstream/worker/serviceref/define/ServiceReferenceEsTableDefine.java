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

package org.skywalking.apm.collector.agentstream.worker.serviceref.define;

import org.skywalking.apm.collector.storage.define.serviceref.ServiceReferenceTable;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceEsTableDefine extends ElasticSearchTableDefine {

    public ServiceReferenceEsTableDefine() {
        super(ServiceReferenceTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 2;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_AGG, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID, ElasticSearchColumnDefine.Type.Integer.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_S1_LTE, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_S3_LTE, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_S5_LTE, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_S5_GT, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_SUMMARY, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_ERROR, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_COST_SUMMARY, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(ServiceReferenceTable.COLUMN_TIME_BUCKET, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
