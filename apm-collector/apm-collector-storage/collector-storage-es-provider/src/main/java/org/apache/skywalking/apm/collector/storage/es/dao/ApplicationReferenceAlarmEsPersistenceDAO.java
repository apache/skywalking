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
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarmTable;
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
public class ApplicationReferenceAlarmEsPersistenceDAO extends EsDAO implements IApplicationReferenceAlarmPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ApplicationReferenceAlarm> {

    private final Logger logger = LoggerFactory.getLogger(ApplicationReferenceAlarmEsPersistenceDAO.class);

    public ApplicationReferenceAlarmEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public ApplicationReferenceAlarm get(String id) {
        GetResponse getResponse = getClient().prepareGet(ApplicationReferenceAlarmTable.TABLE, id).get();
        if (getResponse.isExists()) {
            ApplicationReferenceAlarm applicationReferenceAlarm = new ApplicationReferenceAlarm();
            applicationReferenceAlarm.setId(id);
            Map<String, Object> source = getResponse.getSource();
            applicationReferenceAlarm.setFrontApplicationId(((Number)source.get(ApplicationReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID)).intValue());
            applicationReferenceAlarm.setBehindApplicationId(((Number)source.get(ApplicationReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID)).intValue());
            applicationReferenceAlarm.setSourceValue(((Number)source.get(ApplicationReferenceAlarmTable.COLUMN_SOURCE_VALUE)).intValue());

            applicationReferenceAlarm.setAlarmType(((Number)source.get(ApplicationReferenceAlarmTable.COLUMN_ALARM_TYPE)).intValue());
            applicationReferenceAlarm.setAlarmContent((String)source.get(ApplicationReferenceAlarmTable.COLUMN_ALARM_CONTENT));

            applicationReferenceAlarm.setLastTimeBucket(((Number)source.get(ApplicationReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET)).longValue());
            return applicationReferenceAlarm;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(ApplicationReferenceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ApplicationReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(ApplicationReferenceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ApplicationReferenceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ApplicationReferenceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ApplicationReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return getClient().prepareIndex(ApplicationReferenceAlarmTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(ApplicationReferenceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ApplicationReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(ApplicationReferenceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ApplicationReferenceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ApplicationReferenceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ApplicationReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return getClient().prepareUpdate(ApplicationReferenceAlarmTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(ApplicationReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(ApplicationReferenceAlarmTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, ApplicationReferenceAlarmTable.TABLE);
    }
}
