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
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.ICpuMetricPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.table.jvm.CpuMetric;
import org.apache.skywalking.apm.collector.storage.table.jvm.CpuMetricTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class CpuMetricEsPersistenceDAO extends EsDAO implements ICpuMetricPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, CpuMetric> {

    private final Logger logger = LoggerFactory.getLogger(CpuMetricEsPersistenceDAO.class);

    public CpuMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public CpuMetric get(String id) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(CpuMetric cpuMetric) {
        Map<String, Object> source = new HashMap<>();
        source.put(CpuMetricTable.COLUMN_INSTANCE_ID, cpuMetric.getInstanceId());
        source.put(CpuMetricTable.COLUMN_USAGE_PERCENT, cpuMetric.getUsagePercent());
        source.put(CpuMetricTable.COLUMN_TIME_BUCKET, cpuMetric.getTimeBucket());

        logger.debug("prepare cpu metric batch insert, getId: {}", cpuMetric.getId());
        return getClient().prepareIndex(CpuMetricTable.TABLE, cpuMetric.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(CpuMetric cpuMetric) {
        return null;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(CpuMetricTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(CpuMetricTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, CpuMetricTable.TABLE);
    }
}
