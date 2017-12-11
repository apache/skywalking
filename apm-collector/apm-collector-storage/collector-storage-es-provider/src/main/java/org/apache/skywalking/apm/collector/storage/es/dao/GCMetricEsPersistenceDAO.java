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
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetric;
import org.apache.skywalking.apm.collector.storage.table.jvm.GCMetricTable;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.IGCMetricPersistenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class GCMetricEsPersistenceDAO extends EsDAO implements IGCMetricPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, GCMetric> {

    private final Logger logger = LoggerFactory.getLogger(GCMetricEsPersistenceDAO.class);

    public GCMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public GCMetric get(String id) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(GCMetric gcMetric) {
        Map<String, Object> source = new HashMap<>();
        source.put(GCMetricTable.COLUMN_INSTANCE_ID, gcMetric.getInstanceId());
        source.put(GCMetricTable.COLUMN_PHRASE, gcMetric.getPhrase());
        source.put(GCMetricTable.COLUMN_COUNT, gcMetric.getCount());
        source.put(GCMetricTable.COLUMN_TIME, gcMetric.getTime());
        source.put(GCMetricTable.COLUMN_TIME_BUCKET, gcMetric.getTimeBucket());

        return getClient().prepareIndex(GCMetricTable.TABLE, gcMetric.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(GCMetric gcMetric) {
        return null;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(GCMetricTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(GCMetricTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, GCMetricTable.TABLE);
    }
}
