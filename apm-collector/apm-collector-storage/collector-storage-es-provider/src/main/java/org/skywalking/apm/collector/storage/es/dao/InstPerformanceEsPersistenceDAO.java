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
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.dao.IInstPerformancePersistenceDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.instance.InstPerformance;
import org.skywalking.apm.collector.storage.table.instance.InstPerformanceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstPerformanceEsPersistenceDAO extends EsDAO implements IInstPerformancePersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, InstPerformance> {

    private final Logger logger = LoggerFactory.getLogger(InstPerformanceEsPersistenceDAO.class);

    public InstPerformanceEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public InstPerformance get(String id) {
        GetResponse getResponse = getClient().prepareGet(InstPerformanceTable.TABLE, id).get();
        if (getResponse.isExists()) {
            logger.debug("getId: {} is exist", id);
            InstPerformance instPerformance = new InstPerformance(id);
            Map<String, Object> source = getResponse.getSource();
            instPerformance.setApplicationId((Integer)source.get(InstPerformanceTable.COLUMN_APPLICATION_ID));
            instPerformance.setInstanceId((Integer)source.get(InstPerformanceTable.COLUMN_INSTANCE_ID));
            instPerformance.setCalls((Integer)source.get(InstPerformanceTable.COLUMN_CALLS));
            instPerformance.setCostTotal(((Number)source.get(InstPerformanceTable.COLUMN_COST_TOTAL)).longValue());
            instPerformance.setTimeBucket(((Number)source.get(InstPerformanceTable.COLUMN_TIME_BUCKET)).longValue());
            return instPerformance;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(InstPerformance data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstPerformanceTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstPerformanceTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(InstPerformanceTable.COLUMN_CALLS, data.getCalls());
        source.put(InstPerformanceTable.COLUMN_COST_TOTAL, data.getCostTotal());
        source.put(InstPerformanceTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(InstPerformanceTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(InstPerformance data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstPerformanceTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstPerformanceTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(InstPerformanceTable.COLUMN_CALLS, data.getCalls());
        source.put(InstPerformanceTable.COLUMN_COST_TOTAL, data.getCostTotal());
        source.put(InstPerformanceTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(InstPerformanceTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(InstPerformanceTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(InstPerformanceTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, InstPerformanceTable.TABLE);
    }
}
