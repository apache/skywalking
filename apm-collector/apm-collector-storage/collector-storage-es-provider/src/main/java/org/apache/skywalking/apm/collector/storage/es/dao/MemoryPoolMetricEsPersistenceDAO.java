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


package org.apache.skywalking.apm.collector.storage.es.dao;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.storage.dao.IMemoryPoolMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetric;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetricTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class MemoryPoolMetricEsPersistenceDAO extends EsDAO implements IMemoryPoolMetricPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, MemoryPoolMetric> {

    private final Logger logger = LoggerFactory.getLogger(MemoryPoolMetricEsPersistenceDAO.class);

    public MemoryPoolMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public MemoryPoolMetric get(String id) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(MemoryPoolMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(MemoryPoolMetricTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(MemoryPoolMetricTable.COLUMN_POOL_TYPE, data.getPoolType());
        source.put(MemoryPoolMetricTable.COLUMN_INIT, data.getInit());
        source.put(MemoryPoolMetricTable.COLUMN_MAX, data.getMax());
        source.put(MemoryPoolMetricTable.COLUMN_USED, data.getUsed());
        source.put(MemoryPoolMetricTable.COLUMN_COMMITTED, data.getCommitted());
        source.put(MemoryPoolMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(MemoryPoolMetricTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(MemoryPoolMetric data) {
        return null;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(MemoryPoolMetricTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(MemoryPoolMetricTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, MemoryPoolMetricTable.TABLE);
    }
}
