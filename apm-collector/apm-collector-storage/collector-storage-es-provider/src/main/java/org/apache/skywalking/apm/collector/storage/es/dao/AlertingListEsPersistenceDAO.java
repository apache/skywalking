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
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.IAlertingListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.table.alerting.AlertingList;
import org.apache.skywalking.apm.collector.storage.table.alerting.AlertingListTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class AlertingListEsPersistenceDAO extends EsDAO implements IAlertingListPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, AlertingList> {

    private final Logger logger = LoggerFactory.getLogger(AlertingListEsPersistenceDAO.class);

    public AlertingListEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public AlertingList get(String id) {
        GetResponse getResponse = getClient().prepareGet(AlertingListTable.TABLE, id).get();
        if (getResponse.isExists()) {
            AlertingList alertingList = new AlertingList(id);
            Map<String, Object> source = getResponse.getSource();
            alertingList.setLayer(((Number)source.get(AlertingListTable.COLUMN_LAYER)).intValue());
            alertingList.setLayerId(((Number)source.get(AlertingListTable.COLUMN_LAYER_ID)).intValue());
            alertingList.setFirstTimeBucket(((Number)source.get(AlertingListTable.COLUMN_FIRST_TIME_BUCKET)).longValue());
            alertingList.setLastTimeBucket(((Number)source.get(AlertingListTable.COLUMN_LAST_TIME_BUCKET)).longValue());
            alertingList.setExpected(((Number)source.get(AlertingListTable.COLUMN_EXPECTED)).intValue());
            alertingList.setActual(((Number)source.get(AlertingListTable.COLUMN_ACTUAL)).intValue());
            alertingList.setValid((Boolean)source.get(AlertingListTable.COLUMN_VALID));
            return alertingList;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(AlertingList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(AlertingListTable.COLUMN_LAYER, data.getLayer());
        source.put(AlertingListTable.COLUMN_LAYER_ID, data.getLayerId());
        source.put(AlertingListTable.COLUMN_FIRST_TIME_BUCKET, data.getFirstTimeBucket());
        source.put(AlertingListTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());
        source.put(AlertingListTable.COLUMN_EXPECTED, data.getExpected());
        source.put(AlertingListTable.COLUMN_ACTUAL, data.getActual());
        source.put(AlertingListTable.COLUMN_VALID, data.getValid());

        return getClient().prepareIndex(AlertingListTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(AlertingList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(AlertingListTable.COLUMN_LAYER, data.getLayer());
        source.put(AlertingListTable.COLUMN_LAYER_ID, data.getLayerId());
        source.put(AlertingListTable.COLUMN_FIRST_TIME_BUCKET, data.getFirstTimeBucket());
        source.put(AlertingListTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());
        source.put(AlertingListTable.COLUMN_EXPECTED, data.getExpected());
        source.put(AlertingListTable.COLUMN_ACTUAL, data.getActual());
        source.put(AlertingListTable.COLUMN_VALID, data.getValid());

        return getClient().prepareUpdate(AlertingListTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(AlertingListTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(AlertingListTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, AlertingListTable.TABLE);
    }
}
