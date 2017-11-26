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
import org.skywalking.apm.collector.storage.dao.IInstanceMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.instance.InstanceMetric;
import org.skywalking.apm.collector.storage.table.instance.InstanceMetricTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class InstanceMetricEsPersistenceDAO extends EsDAO implements IInstanceMetricPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, InstanceMetric> {

    private final Logger logger = LoggerFactory.getLogger(InstanceMetricEsPersistenceDAO.class);

    public InstanceMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public InstanceMetric get(String id) {
        GetResponse getResponse = getClient().prepareGet(InstanceMetricTable.TABLE, id).get();
        if (getResponse.isExists()) {
            logger.debug("getId: {} is exist", id);
            InstanceMetric instanceMetric = new InstanceMetric(id);
            Map<String, Object> source = getResponse.getSource();
            instanceMetric.setApplicationId((Integer)source.get(InstanceMetricTable.COLUMN_APPLICATION_ID));
            instanceMetric.setInstanceId((Integer)source.get(InstanceMetricTable.COLUMN_INSTANCE_ID));
            instanceMetric.setCalls((Integer)source.get(InstanceMetricTable.COLUMN_CALLS));
            instanceMetric.setErrorCalls(((Number)source.get(InstanceMetricTable.COLUMN_ERROR_CALLS)).longValue());
            instanceMetric.setDurationSum(((Number)source.get(InstanceMetricTable.COLUMN_DURATION_SUM)).longValue());
            instanceMetric.setErrorDurationSum(((Number)source.get(InstanceMetricTable.COLUMN_ERROR_DURATION_SUM)).longValue());
            instanceMetric.setTimeBucket(((Number)source.get(InstanceMetricTable.COLUMN_TIME_BUCKET)).longValue());
            return instanceMetric;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(InstanceMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceMetricTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceMetricTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(InstanceMetricTable.COLUMN_CALLS, data.getCalls());
        source.put(InstanceMetricTable.COLUMN_ERROR_CALLS, data.getErrorCalls());
        source.put(InstanceMetricTable.COLUMN_DURATION_SUM, data.getDurationSum());
        source.put(InstanceMetricTable.COLUMN_ERROR_DURATION_SUM, data.getErrorDurationSum());
        source.put(InstanceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(InstanceMetricTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(InstanceMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceMetricTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceMetricTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(InstanceMetricTable.COLUMN_CALLS, data.getCalls());
        source.put(InstanceMetricTable.COLUMN_ERROR_CALLS, data.getErrorCalls());
        source.put(InstanceMetricTable.COLUMN_DURATION_SUM, data.getDurationSum());
        source.put(InstanceMetricTable.COLUMN_ERROR_DURATION_SUM, data.getErrorDurationSum());
        source.put(InstanceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(InstanceMetricTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getSecondTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(InstanceMetricTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(InstanceMetricTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, InstanceMetricTable.TABLE);
    }
}
