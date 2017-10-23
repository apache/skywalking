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

package org.skywalking.apm.collector.agentstream.worker.serviceref.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.serviceref.ServiceReferenceTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceEsDAO extends EsDAO implements IServiceReferenceDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceEsDAO.class);

    @Override public Data get(String id, DataDefine dataDefine) {
        GetResponse getResponse = getClient().prepareGet(ServiceReferenceTable.TABLE, id).get();
        if (getResponse.isExists()) {
            Data data = dataDefine.build(id);
            Map<String, Object> source = getResponse.getSource();
            data.setDataInteger(0, ((Number)source.get(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID)).intValue());
            data.setDataString(1, (String)source.get(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME));
            data.setDataInteger(1, ((Number)source.get(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID)).intValue());
            data.setDataString(2, (String)source.get(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME));
            data.setDataInteger(2, ((Number)source.get(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID)).intValue());
            data.setDataString(3, (String)source.get(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME));
            data.setDataLong(0, ((Number)source.get(ServiceReferenceTable.COLUMN_S1_LTE)).longValue());
            data.setDataLong(1, ((Number)source.get(ServiceReferenceTable.COLUMN_S3_LTE)).longValue());
            data.setDataLong(2, ((Number)source.get(ServiceReferenceTable.COLUMN_S5_LTE)).longValue());
            data.setDataLong(3, ((Number)source.get(ServiceReferenceTable.COLUMN_S5_GT)).longValue());
            data.setDataLong(4, ((Number)source.get(ServiceReferenceTable.COLUMN_SUMMARY)).longValue());
            data.setDataLong(5, ((Number)source.get(ServiceReferenceTable.COLUMN_ERROR)).longValue());
            data.setDataLong(6, ((Number)source.get(ServiceReferenceTable.COLUMN_COST_SUMMARY)).longValue());
            data.setDataLong(7, ((Number)source.get(ServiceReferenceTable.COLUMN_TIME_BUCKET)).longValue());
            return data;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, data.getDataInteger(0));
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, data.getDataString(1));
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, data.getDataInteger(1));
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, data.getDataString(2));
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID, data.getDataInteger(2));
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME, data.getDataString(3));
        source.put(ServiceReferenceTable.COLUMN_S1_LTE, data.getDataLong(0));
        source.put(ServiceReferenceTable.COLUMN_S3_LTE, data.getDataLong(1));
        source.put(ServiceReferenceTable.COLUMN_S5_LTE, data.getDataLong(2));
        source.put(ServiceReferenceTable.COLUMN_S5_GT, data.getDataLong(3));
        source.put(ServiceReferenceTable.COLUMN_SUMMARY, data.getDataLong(4));
        source.put(ServiceReferenceTable.COLUMN_ERROR, data.getDataLong(5));
        source.put(ServiceReferenceTable.COLUMN_COST_SUMMARY, data.getDataLong(6));
        source.put(ServiceReferenceTable.COLUMN_TIME_BUCKET, data.getDataLong(7));

        return getClient().prepareIndex(ServiceReferenceTable.TABLE, data.getDataString(0)).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, data.getDataInteger(0));
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, data.getDataString(1));
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, data.getDataInteger(1));
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, data.getDataString(2));
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID, data.getDataInteger(2));
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME, data.getDataString(3));
        source.put(ServiceReferenceTable.COLUMN_S1_LTE, data.getDataLong(0));
        source.put(ServiceReferenceTable.COLUMN_S3_LTE, data.getDataLong(1));
        source.put(ServiceReferenceTable.COLUMN_S5_LTE, data.getDataLong(2));
        source.put(ServiceReferenceTable.COLUMN_S5_GT, data.getDataLong(3));
        source.put(ServiceReferenceTable.COLUMN_SUMMARY, data.getDataLong(4));
        source.put(ServiceReferenceTable.COLUMN_ERROR, data.getDataLong(5));
        source.put(ServiceReferenceTable.COLUMN_COST_SUMMARY, data.getDataLong(6));
        source.put(ServiceReferenceTable.COLUMN_TIME_BUCKET, data.getDataLong(7));

        return getClient().prepareUpdate(ServiceReferenceTable.TABLE, data.getDataString(0)).setDoc(source);
    }
}
