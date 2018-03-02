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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmListTable;
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
public class ServiceReferenceAlarmListEsPersistenceDAO extends EsHttpDAO implements IServiceReferenceAlarmListPersistenceDAO<Index, Update, ServiceReferenceAlarmList> {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceAlarmListEsPersistenceDAO.class);

    public ServiceReferenceAlarmListEsPersistenceDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public ServiceReferenceAlarmList get(String id) {
        DocumentResult getResponse = getClient().prepareGet(ServiceReferenceAlarmListTable.TABLE, id);
        if (getResponse.isSucceeded()) {
            ServiceReferenceAlarmList serviceReferenceAlarmList = new ServiceReferenceAlarmList();
            serviceReferenceAlarmList.setId(id);
            JsonObject source = getResponse.getSourceAsObject(JsonObject.class);
            serviceReferenceAlarmList.setFrontApplicationId((source.get(ServiceReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID)).getAsInt());
            serviceReferenceAlarmList.setBehindApplicationId((source.get(ServiceReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID)).getAsInt());
            serviceReferenceAlarmList.setFrontInstanceId((source.get(ServiceReferenceAlarmListTable.COLUMN_FRONT_INSTANCE_ID)).getAsInt());
            serviceReferenceAlarmList.setBehindInstanceId((source.get(ServiceReferenceAlarmListTable.COLUMN_BEHIND_INSTANCE_ID)).getAsInt());
            serviceReferenceAlarmList.setFrontServiceId((source.get(ServiceReferenceAlarmListTable.COLUMN_FRONT_SERVICE_ID)).getAsInt());
            serviceReferenceAlarmList.setBehindServiceId((source.get(ServiceReferenceAlarmListTable.COLUMN_BEHIND_SERVICE_ID)).getAsInt());
            serviceReferenceAlarmList.setSourceValue((source.get(ServiceReferenceAlarmListTable.COLUMN_SOURCE_VALUE)).getAsInt());

            serviceReferenceAlarmList.setAlarmType((source.get(ServiceReferenceAlarmListTable.COLUMN_ALARM_TYPE)).getAsInt());
            serviceReferenceAlarmList.setAlarmContent(source.get(ServiceReferenceAlarmListTable.COLUMN_ALARM_CONTENT).getAsString());

            serviceReferenceAlarmList.setTimeBucket((source.get(ServiceReferenceAlarmListTable.COLUMN_TIME_BUCKET)).getAsLong());
            return serviceReferenceAlarmList;
        } else {
            return null;
        }
    }

    @Override public Index prepareBatchInsert(ServiceReferenceAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_FRONT_SERVICE_ID, data.getFrontServiceId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_BEHIND_SERVICE_ID, data.getBehindServiceId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceReferenceAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ServiceReferenceAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ServiceReferenceAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return new Index.Builder(source).index(ServiceReferenceAlarmListTable.TABLE).type(ServiceReferenceAlarmListTable.TABLE_TYPE).id(data.getId()).build();
    }

    @Override public Update prepareBatchUpdate(ServiceReferenceAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_FRONT_SERVICE_ID, data.getFrontServiceId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_BEHIND_SERVICE_ID, data.getBehindServiceId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceReferenceAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ServiceReferenceAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ServiceReferenceAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return new Update.Builder(source).index(ServiceReferenceAlarmListTable.TABLE).id(data.getId()).build();
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);

        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(ServiceReferenceAlarmListTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));

        long deleted =  getClient().batchDelete(ServiceReferenceAlarmListTable.TABLE, searchSourceBuilder.toString());
        logger.info("Delete {} rows history from {} index.", deleted, ServiceReferenceAlarmListTable.TABLE);
    }
}
