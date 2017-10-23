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

package org.skywalking.apm.collector.agentjvm.worker.memorypool.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.jvm.MemoryPoolMetricTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;

/**
 * @author peng-yongsheng
 */
public class MemoryPoolMetricEsDAO extends EsDAO implements IMemoryPoolMetricDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(MemoryPoolMetricTable.COLUMN_INSTANCE_ID, data.getDataInteger(0));
        source.put(MemoryPoolMetricTable.COLUMN_POOL_TYPE, data.getDataInteger(1));
        source.put(MemoryPoolMetricTable.COLUMN_INIT, data.getDataLong(0));
        source.put(MemoryPoolMetricTable.COLUMN_MAX, data.getDataLong(1));
        source.put(MemoryPoolMetricTable.COLUMN_USED, data.getDataLong(2));
        source.put(MemoryPoolMetricTable.COLUMN_COMMITTED, data.getDataLong(3));
        source.put(MemoryPoolMetricTable.COLUMN_TIME_BUCKET, data.getDataLong(4));

        return getClient().prepareIndex(MemoryPoolMetricTable.TABLE, data.getDataString(0)).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        return null;
    }
}
