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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarmTable;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import io.searchbox.core.DocumentResult;
import io.searchbox.core.Index;
import io.searchbox.core.Update;

/**
 * @author cyberdak
 */
public class ApplicationReferenceAlarmEsPersistenceDAO extends EsHttpDAO implements IApplicationReferenceAlarmPersistenceDAO<Index, Update, ApplicationReferenceAlarm> {

    private final Logger logger = LoggerFactory.getLogger(ApplicationReferenceAlarmEsPersistenceDAO.class);

    public ApplicationReferenceAlarmEsPersistenceDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public ApplicationReferenceAlarm get(String id) {
        DocumentResult getResponse = getClient().prepareGet(ApplicationReferenceAlarmTable.TABLE, id);
        if (getResponse.isSucceeded()) {
            ApplicationReferenceAlarm applicationReferenceAlarm = new ApplicationReferenceAlarm();
            applicationReferenceAlarm.setId(id);
            JsonObject source = getResponse.getSourceAsObject(JsonObject.class);
            applicationReferenceAlarm.setFrontApplicationId((source.get(ApplicationReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID)).getAsInt());
            applicationReferenceAlarm.setBehindApplicationId((source.get(ApplicationReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID)).getAsInt());
            applicationReferenceAlarm.setSourceValue((source.get(ApplicationReferenceAlarmTable.COLUMN_SOURCE_VALUE)).getAsInt());

            applicationReferenceAlarm.setAlarmType((source.get(ApplicationReferenceAlarmTable.COLUMN_ALARM_TYPE)).getAsInt());
            applicationReferenceAlarm.setAlarmContent((String)source.get(ApplicationReferenceAlarmTable.COLUMN_ALARM_CONTENT).getAsString());

            applicationReferenceAlarm.setLastTimeBucket((source.get(ApplicationReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET)).getAsLong());
            return applicationReferenceAlarm;
        } else {
            return null;
        }
    }

    @Override public Index prepareBatchInsert(ApplicationReferenceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ApplicationReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(ApplicationReferenceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ApplicationReferenceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ApplicationReferenceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ApplicationReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return new Index.Builder(source).index(ApplicationReferenceAlarmTable.TABLE).type(ApplicationReferenceAlarmTable.TABLE_TYPE).id(data.getId()).build();
    }

    @Override public Update prepareBatchUpdate(ApplicationReferenceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ApplicationReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(ApplicationReferenceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ApplicationReferenceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ApplicationReferenceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ApplicationReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());
        
        Map<String, Object> doc = new HashMap<>();
        doc.put("doc", source);

        return new Update.Builder(doc).index(ApplicationReferenceAlarmTable.TABLE).type(ApplicationReferenceAlarmTable.TABLE_TYPE).id(data.getId()).build();
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(ApplicationReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));

        long deleted = getClient().batchDelete(ApplicationReferenceAlarmTable.TABLE, searchSourceBuilder.toString());
        logger.info("Delete {} rows history from {} index.", deleted, ApplicationReferenceAlarmTable.TABLE);
    }
}
