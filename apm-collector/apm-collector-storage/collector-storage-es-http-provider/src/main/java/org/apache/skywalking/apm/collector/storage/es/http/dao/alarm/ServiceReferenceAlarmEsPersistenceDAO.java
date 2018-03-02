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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmTable;
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
public class ServiceReferenceAlarmEsPersistenceDAO extends EsHttpDAO implements IServiceReferenceAlarmPersistenceDAO<Index, Update, ServiceReferenceAlarm> {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceAlarmEsPersistenceDAO.class);

    public ServiceReferenceAlarmEsPersistenceDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public ServiceReferenceAlarm get(String id) {
        DocumentResult getResponse = getClient().prepareGet(ServiceReferenceAlarmTable.TABLE, id);
        if (getResponse.isSucceeded()) {
            ServiceReferenceAlarm serviceReferenceAlarm = new ServiceReferenceAlarm();
            serviceReferenceAlarm.setId(id);
            JsonObject source = getResponse.getSourceAsObject(JsonObject.class);
            serviceReferenceAlarm.setFrontApplicationId((source.get(ServiceReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID)).getAsInt());
            serviceReferenceAlarm.setBehindApplicationId((source.get(ServiceReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID)).getAsInt());
            serviceReferenceAlarm.setFrontInstanceId((source.get(ServiceReferenceAlarmTable.COLUMN_FRONT_INSTANCE_ID)).getAsInt());
            serviceReferenceAlarm.setBehindInstanceId((source.get(ServiceReferenceAlarmTable.COLUMN_BEHIND_INSTANCE_ID)).getAsInt());
            serviceReferenceAlarm.setFrontServiceId((source.get(ServiceReferenceAlarmTable.COLUMN_FRONT_SERVICE_ID)).getAsInt());
            serviceReferenceAlarm.setBehindServiceId((source.get(ServiceReferenceAlarmTable.COLUMN_BEHIND_SERVICE_ID)).getAsInt());
            serviceReferenceAlarm.setSourceValue((source.get(ServiceReferenceAlarmTable.COLUMN_SOURCE_VALUE)).getAsInt());

            serviceReferenceAlarm.setAlarmType((source.get(ServiceReferenceAlarmTable.COLUMN_ALARM_TYPE)).getAsInt());
            serviceReferenceAlarm.setAlarmContent(source.get(ServiceReferenceAlarmTable.COLUMN_ALARM_CONTENT).getAsString());

            serviceReferenceAlarm.setLastTimeBucket((source.get(ServiceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET)).getAsLong());
            return serviceReferenceAlarm;
        } else {
            return null;
        }
    }

    @Override public Index prepareBatchInsert(ServiceReferenceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_SERVICE_ID, data.getFrontServiceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_SERVICE_ID, data.getBehindServiceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceReferenceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ServiceReferenceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ServiceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return new Index.Builder(source).index(ServiceReferenceAlarmTable.TABLE).type(ServiceReferenceAlarmTable.TABLE_TYPE).id(data.getId()).build();
    }

    @Override public Update prepareBatchUpdate(ServiceReferenceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_SERVICE_ID, data.getFrontServiceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_SERVICE_ID, data.getBehindServiceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceReferenceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ServiceReferenceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ServiceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return new Update.Builder(source).index(ServiceReferenceAlarmTable.TABLE).id(data.getId()).build();
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(ServiceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));
        
        long deleted = getClient().batchDelete(ServiceReferenceAlarmTable.TABLE, searchSourceBuilder.toString());
        logger.info("Delete {} rows history from {} index.", deleted, ServiceReferenceAlarmTable.TABLE);
    }
}
