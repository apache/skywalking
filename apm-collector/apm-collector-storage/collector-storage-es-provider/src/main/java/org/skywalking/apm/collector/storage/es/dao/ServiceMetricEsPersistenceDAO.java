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
import org.skywalking.apm.collector.storage.dao.IServiceMetricPersistenceDAO;
import org.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.service.ServiceMetric;
import org.skywalking.apm.collector.storage.table.service.ServiceMetricTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ServiceMetricEsPersistenceDAO extends EsDAO implements IServiceMetricPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ServiceMetric> {

    private final Logger logger = LoggerFactory.getLogger(ServiceMetricEsPersistenceDAO.class);

    public ServiceMetricEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public ServiceMetric get(String id) {
        GetResponse getResponse = getClient().prepareGet(ServiceMetricTable.TABLE, id).get();
        if (getResponse.isExists()) {
            ServiceMetric serviceMetric = new ServiceMetric(id);
            Map<String, Object> source = getResponse.getSource();
            serviceMetric.setServiceId(((Number)source.get(ServiceMetricTable.COLUMN_SERVICE_ID)).intValue());
            serviceMetric.setCalls(((Number)source.get(ServiceMetricTable.COLUMN_CALLS)).longValue());
            serviceMetric.setErrorCalls(((Number)source.get(ServiceMetricTable.COLUMN_ERROR_CALLS)).longValue());
            serviceMetric.setDurationSum(((Number)source.get(ServiceMetricTable.COLUMN_DURATION_SUM)).longValue());
            serviceMetric.setErrorDurationSum(((Number)source.get(ServiceMetricTable.COLUMN_ERROR_DURATION_SUM)).longValue());
            serviceMetric.setTimeBucket(((Number)source.get(ServiceMetricTable.COLUMN_TIME_BUCKET)).longValue());
            return serviceMetric;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(ServiceMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceMetricTable.COLUMN_SERVICE_ID, data.getServiceId());
        source.put(ServiceMetricTable.COLUMN_CALLS, data.getCalls());
        source.put(ServiceMetricTable.COLUMN_ERROR_CALLS, data.getErrorCalls());
        source.put(ServiceMetricTable.COLUMN_DURATION_SUM, data.getDurationSum());
        source.put(ServiceMetricTable.COLUMN_ERROR_DURATION_SUM, data.getErrorDurationSum());
        source.put(ServiceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(ServiceMetricTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(ServiceMetric data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceMetricTable.COLUMN_SERVICE_ID, data.getServiceId());
        source.put(ServiceMetricTable.COLUMN_CALLS, data.getCalls());
        source.put(ServiceMetricTable.COLUMN_ERROR_CALLS, data.getErrorCalls());
        source.put(ServiceMetricTable.COLUMN_DURATION_SUM, data.getDurationSum());
        source.put(ServiceMetricTable.COLUMN_ERROR_DURATION_SUM, data.getErrorDurationSum());
        source.put(ServiceMetricTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(ServiceMetricTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(ServiceMetricTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(ServiceMetricTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, ServiceMetricTable.TABLE);
    }
}
