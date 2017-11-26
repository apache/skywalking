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
import org.skywalking.apm.collector.storage.dao.IApplicationMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.application.ApplicationMetric;
import org.skywalking.apm.collector.storage.table.application.ApplicationMetricTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationMetricEsPersistenceDAO extends EsDAO implements IApplicationMetricPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ApplicationMetric> {

    private final Logger logger = LoggerFactory.getLogger(ApplicationMetricEsPersistenceDAO.class);

    public ApplicationMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public ApplicationMetric get(String id) {
        GetResponse getResponse = getClient().prepareGet(ApplicationMetricTable.TABLE, id).get();
        if (getResponse.isExists()) {
            ApplicationMetric applicationMetric = new ApplicationMetric(id);
            Map<String, Object> source = getResponse.getSource();
            applicationMetric.setApplicationId(((Number)source.get(ApplicationMetricTable.COLUMN_APPLICATION_ID)).intValue());
            applicationMetric.setCalls(((Number)source.get(ApplicationMetricTable.COLUMN_CALLS)).longValue());
            applicationMetric.setErrorCalls(((Number)source.get(ApplicationMetricTable.COLUMN_ERROR_CALLS)).longValue());
            applicationMetric.setDurationSum(((Number)source.get(ApplicationMetricTable.COLUMN_DURATION_SUM)).longValue());
            applicationMetric.setErrorDurationSum(((Number)source.get(ApplicationMetricTable.COLUMN_ERROR_DURATION_SUM)).longValue());
            applicationMetric.setSatisfiedCount(((Number)source.get(ApplicationMetricTable.COLUMN_SATISFIED_COUNT)).longValue());
            applicationMetric.setToleratingCount(((Number)source.get(ApplicationMetricTable.COLUMN_TOLERATING_COUNT)).longValue());
            applicationMetric.setFrustratedCount(((Number)source.get(ApplicationMetricTable.COLUMN_FRUSTRATED_COUNT)).longValue());
            applicationMetric.setTimeBucket(((Number)source.get(ApplicationMetricTable.COLUMN_TIME_BUCKET)).longValue());
            return applicationMetric;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(ApplicationMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationMetricTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ApplicationMetricTable.COLUMN_CALLS, data.getCalls());
        source.put(ApplicationMetricTable.COLUMN_ERROR_CALLS, data.getErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_DURATION_SUM, data.getDurationSum());
        source.put(ApplicationMetricTable.COLUMN_ERROR_DURATION_SUM, data.getErrorDurationSum());
        source.put(ApplicationMetricTable.COLUMN_SATISFIED_COUNT, data.getSatisfiedCount());
        source.put(ApplicationMetricTable.COLUMN_TOLERATING_COUNT, data.getToleratingCount());
        source.put(ApplicationMetricTable.COLUMN_FRUSTRATED_COUNT, data.getFrustratedCount());
        source.put(ApplicationMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(ApplicationMetricTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(ApplicationMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationMetricTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ApplicationMetricTable.COLUMN_CALLS, data.getCalls());
        source.put(ApplicationMetricTable.COLUMN_ERROR_CALLS, data.getErrorCalls());
        source.put(ApplicationMetricTable.COLUMN_DURATION_SUM, data.getDurationSum());
        source.put(ApplicationMetricTable.COLUMN_ERROR_DURATION_SUM, data.getErrorDurationSum());
        source.put(ApplicationMetricTable.COLUMN_SATISFIED_COUNT, data.getSatisfiedCount());
        source.put(ApplicationMetricTable.COLUMN_TOLERATING_COUNT, data.getToleratingCount());
        source.put(ApplicationMetricTable.COLUMN_FRUSTRATED_COUNT, data.getFrustratedCount());

        return getClient().prepareUpdate(ApplicationMetricTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(ApplicationMetricTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(ApplicationMetricTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, ApplicationMetricTable.TABLE);
    }
}
