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

package org.skywalking.apm.collector.agentjvm.worker.heartbeat.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.core.framework.UnexpectedException;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.register.InstanceTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceHeartBeatEsDAO extends EsDAO implements IInstanceHeartBeatDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    private final Logger logger = LoggerFactory.getLogger(InstanceHeartBeatEsDAO.class);

    @Override public Data get(String id, DataDefine dataDefine) {
        GetResponse getResponse = getClient().prepareGet(InstanceTable.TABLE, id).get();
        if (getResponse.isExists()) {
            Data data = dataDefine.build(id);
            Map<String, Object> source = getResponse.getSource();
            data.setDataInteger(0, (Integer)source.get(InstanceTable.COLUMN_INSTANCE_ID));
            data.setDataLong(0, (Long)source.get(InstanceTable.COLUMN_HEARTBEAT_TIME));
            logger.debug("id: {} is exists", id);
            return data;
        } else {
            logger.debug("id: {} is not exists", id);
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceTable.COLUMN_HEARTBEAT_TIME, data.getDataLong(0));
        return getClient().prepareUpdate(InstanceTable.TABLE, data.getDataString(0)).setDoc(source);
    }
}
