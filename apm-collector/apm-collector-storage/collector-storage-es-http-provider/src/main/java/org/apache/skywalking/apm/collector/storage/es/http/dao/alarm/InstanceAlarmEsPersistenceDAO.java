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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmTable;
import org.elasticsearch.index.query.QueryBuilders;
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
public class InstanceAlarmEsPersistenceDAO extends EsHttpDAO implements IInstanceAlarmPersistenceDAO<Index, Update, InstanceAlarm> {

    private final Logger logger = LoggerFactory.getLogger(InstanceAlarmEsPersistenceDAO.class);

    public InstanceAlarmEsPersistenceDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public InstanceAlarm get(String id) {
        DocumentResult getResponse = getClient().prepareGet(InstanceAlarmTable.TABLE, id);
        if (getResponse.isSucceeded()) {
            InstanceAlarm instanceAlarm = new InstanceAlarm();
            instanceAlarm.setId(id);
            JsonObject source = getResponse.getSourceAsObject(JsonObject.class);
            instanceAlarm.setApplicationId((source.get(InstanceAlarmTable.COLUMN_APPLICATION_ID)).getAsInt());
            instanceAlarm.setInstanceId((source.get(InstanceAlarmTable.COLUMN_INSTANCE_ID)).getAsInt());
            instanceAlarm.setSourceValue((source.get(InstanceAlarmTable.COLUMN_SOURCE_VALUE)).getAsInt());

            instanceAlarm.setAlarmType((source.get(InstanceAlarmTable.COLUMN_ALARM_TYPE)).getAsInt());
            instanceAlarm.setAlarmContent((String)source.get(InstanceAlarmTable.COLUMN_ALARM_CONTENT).getAsString());

            instanceAlarm.setLastTimeBucket((source.get(InstanceAlarmTable.COLUMN_LAST_TIME_BUCKET)).getAsLong());
            return instanceAlarm;
        } else {
            return null;
        }
    }

    @Override public Index prepareBatchInsert(InstanceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceAlarmTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceAlarmTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(InstanceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(InstanceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(InstanceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(InstanceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return new Index.Builder(source).index(InstanceAlarmTable.TABLE).type(InstanceAlarmTable.TABLE_TYPE).id(data.getId()).build();
    }

    @Override public Update prepareBatchUpdate(InstanceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceAlarmTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceAlarmTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(InstanceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(InstanceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(InstanceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(InstanceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return new Update.Builder(source).index(InstanceAlarmTable.TABLE).id(data.getId()).build();
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(InstanceAlarmTable.COLUMN_LAST_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));


        
        long deleted =         getClient().batchDelete(InstanceAlarmTable.TABLE, searchSourceBuilder.toString());
        logger.info("Delete {} rows history from {} index.", deleted, InstanceAlarmTable.TABLE);
    }
}
