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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarmTable;
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
public class ServiceAlarmEsPersistenceDAO extends EsHttpDAO implements IServiceAlarmPersistenceDAO<Index, Update, ServiceAlarm> {

    private final Logger logger = LoggerFactory.getLogger(ServiceAlarmEsPersistenceDAO.class);

    public ServiceAlarmEsPersistenceDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public ServiceAlarm get(String id) {
        DocumentResult getResponse = getClient().prepareGet(ServiceAlarmTable.TABLE, id);
        if (getResponse.isSucceeded()) {
            ServiceAlarm serviceAlarm = new ServiceAlarm();
            serviceAlarm.setId(id);
            JsonObject source = getResponse.getSourceAsObject(JsonObject.class);
            serviceAlarm.setApplicationId((source.get(ServiceAlarmTable.COLUMN_APPLICATION_ID)).getAsInt());
            serviceAlarm.setInstanceId((source.get(ServiceAlarmTable.COLUMN_INSTANCE_ID)).getAsInt());
            serviceAlarm.setServiceId((source.get(ServiceAlarmTable.COLUMN_SERVICE_ID)).getAsInt());
            serviceAlarm.setSourceValue((source.get(ServiceAlarmTable.COLUMN_SOURCE_VALUE)).getAsInt());

            serviceAlarm.setAlarmType((source.get(ServiceAlarmTable.COLUMN_ALARM_TYPE)).getAsInt());
            serviceAlarm.setAlarmContent(source.get(ServiceAlarmTable.COLUMN_ALARM_CONTENT).getAsString());

            serviceAlarm.setLastTimeBucket((source.get(ServiceAlarmTable.COLUMN_LAST_TIME_BUCKET)).getAsLong());
            return serviceAlarm;
        } else {
            return null;
        }
    }

    @Override public Index prepareBatchInsert(ServiceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceAlarmTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ServiceAlarmTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(ServiceAlarmTable.COLUMN_SERVICE_ID, data.getServiceId());
        source.put(ServiceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ServiceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ServiceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return new Index.Builder(source).index(ServiceAlarmTable.TABLE).type(ServiceAlarmTable.TABLE_TYPE).id(data.getId()).build();
    }

    @Override public Update prepareBatchUpdate(ServiceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceAlarmTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ServiceAlarmTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(ServiceAlarmTable.COLUMN_SERVICE_ID, data.getServiceId());
        source.put(ServiceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ServiceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ServiceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return new Update.Builder(source).index(ServiceAlarmTable.TABLE).id(data.getId()).build();
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(ServiceAlarmTable.COLUMN_LAST_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));
        
        long deleted = getClient().batchDelete(ServiceAlarmTable.TABLE, searchSourceBuilder.toString());
        logger.info("Delete {} rows history from {} index.", deleted, ServiceAlarmTable.TABLE);
    }
}
