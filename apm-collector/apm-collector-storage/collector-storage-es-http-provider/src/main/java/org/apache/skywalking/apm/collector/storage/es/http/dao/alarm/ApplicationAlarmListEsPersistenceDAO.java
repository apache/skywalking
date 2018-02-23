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

package org.apache.skywalking.apm.collector.storage.es.http.dao.alarm;

import java.util.HashMap;
import java.util.Map;

import org.apache.skywalking.apm.collector.client.elasticsearch.http.ElasticSearchHttpClient;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmListTable;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationAlarmListEsPersistenceDAO extends EsDAO implements IApplicationAlarmListPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ApplicationAlarmList> {

    private final Logger logger = LoggerFactory.getLogger(ApplicationAlarmListEsPersistenceDAO.class);

    public ApplicationAlarmListEsPersistenceDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public ApplicationAlarmList get(String id) {
        GetResponse getResponse = getClient().prepareGet(ApplicationAlarmListTable.TABLE, id).get();
        if (getResponse.isExists()) {
            ApplicationAlarmList applicationAlarmList = new ApplicationAlarmList();
            applicationAlarmList.setId(id);
            Map<String, Object> source = getResponse.getSource();
            applicationAlarmList.setApplicationId(((Number)source.get(ApplicationAlarmListTable.COLUMN_APPLICATION_ID)).intValue());
            applicationAlarmList.setSourceValue(((Number)source.get(ApplicationAlarmListTable.COLUMN_SOURCE_VALUE)).intValue());

            applicationAlarmList.setAlarmType(((Number)source.get(ApplicationAlarmListTable.COLUMN_ALARM_TYPE)).intValue());
            applicationAlarmList.setAlarmContent((String)source.get(ApplicationAlarmListTable.COLUMN_ALARM_CONTENT));

            applicationAlarmList.setTimeBucket(((Number)source.get(ApplicationAlarmListTable.COLUMN_TIME_BUCKET)).longValue());
            return applicationAlarmList;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(ApplicationAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationAlarmListTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ApplicationAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ApplicationAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ApplicationAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ApplicationAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(ApplicationAlarmListTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(ApplicationAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationAlarmListTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ApplicationAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ApplicationAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ApplicationAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ApplicationAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(ApplicationAlarmListTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(ApplicationAlarmListTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(ApplicationAlarmListTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, ApplicationAlarmListTable.TABLE);
    }
}
