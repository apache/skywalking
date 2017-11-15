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

package org.skywalking.apm.collector.storage.es.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.storage.dao.IMemoryMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.jvm.MemoryMetric;
import org.skywalking.apm.collector.storage.table.jvm.MemoryMetricTable;

/**
 * @author peng-yongsheng
 */
public class MemoryMetricEsPersistenceDAO extends EsDAO implements IMemoryMetricPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, MemoryMetric> {

    public MemoryMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public MemoryMetric get(String id) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(MemoryMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(MemoryMetricTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(MemoryMetricTable.COLUMN_IS_HEAP, data.getIsHeap());
        source.put(MemoryMetricTable.COLUMN_INIT, data.getInit());
        source.put(MemoryMetricTable.COLUMN_MAX, data.getMax());
        source.put(MemoryMetricTable.COLUMN_USED, data.getUsed());
        source.put(MemoryMetricTable.COLUMN_COMMITTED, data.getCommitted());
        source.put(MemoryMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(MemoryMetricTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(MemoryMetric data) {
        return null;
    }
}
