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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarmTable;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.JsonObject;

import io.searchbox.core.DocumentResult;
import io.searchbox.core.Index;
import io.searchbox.core.Update;

/**
 * @author peng-yongsheng
 */
public class InstanceReferenceAlarmEsPersistenceDAO extends EsHttpDAO implements IInstanceReferenceAlarmPersistenceDAO<Index, Update, InstanceReferenceAlarm> {

    private final Logger logger = LoggerFactory.getLogger(InstanceReferenceAlarmEsPersistenceDAO.class);

    public InstanceReferenceAlarmEsPersistenceDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public InstanceReferenceAlarm get(String id) {
        DocumentResult getResponse = getClient().prepareGet(InstanceReferenceAlarmTable.TABLE, id);
        if (getResponse.isSucceeded()) {
            InstanceReferenceAlarm instanceReferenceAlarm = new InstanceReferenceAlarm();
            instanceReferenceAlarm.setId(id);
            JsonObject source = getResponse.getSourceAsObject(JsonObject.class);
            instanceReferenceAlarm.setFrontApplicationId((source.get(InstanceReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID)).getAsInt());
            instanceReferenceAlarm.setBehindApplicationId((source.get(InstanceReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID)).getAsInt());
            instanceReferenceAlarm.setFrontInstanceId((source.get(InstanceReferenceAlarmTable.COLUMN_FRONT_INSTANCE_ID)).getAsInt());
            instanceReferenceAlarm.setBehindInstanceId((source.get(InstanceReferenceAlarmTable.COLUMN_BEHIND_INSTANCE_ID)).getAsInt());
            instanceReferenceAlarm.setSourceValue((source.get(InstanceReferenceAlarmTable.COLUMN_SOURCE_VALUE)).getAsInt());

            instanceReferenceAlarm.setAlarmType((source.get(InstanceReferenceAlarmTable.COLUMN_ALARM_TYPE)).getAsInt());
            instanceReferenceAlarm.setAlarmContent(source.get(InstanceReferenceAlarmTable.COLUMN_ALARM_CONTENT).getAsString());

            instanceReferenceAlarm.setLastTimeBucket((source.get(InstanceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET)).getAsLong());
            return instanceReferenceAlarm;
        } else {
            return null;
        }
    }

    @Override public Index prepareBatchInsert(InstanceReferenceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(InstanceReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(InstanceReferenceAlarmTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(InstanceReferenceAlarmTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());
        source.put(InstanceReferenceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(InstanceReferenceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(InstanceReferenceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(InstanceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return new Index.Builder(source).index(InstanceReferenceAlarmTable.TABLE).id(data.getId()).build();
    }

    @Override public Update prepareBatchUpdate(InstanceReferenceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(InstanceReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(InstanceReferenceAlarmTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(InstanceReferenceAlarmTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());
        source.put(InstanceReferenceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(InstanceReferenceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(InstanceReferenceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(InstanceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return new Update.Builder(source).index(InstanceReferenceAlarmTable.TABLE).id(data.getId()).build();
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(InstanceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));

        long deleted = getClient().batchDelete(InstanceReferenceAlarmTable.TABLE, searchSourceBuilder.toString());
        logger.info("Delete {} rows history from {} index.", deleted, InstanceReferenceAlarmTable.TABLE);
    }
}
