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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarmListTable;
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
public class InstanceReferenceAlarmListEsPersistenceDAO extends EsHttpDAO implements IInstanceReferenceAlarmListPersistenceDAO<Index, Update, InstanceReferenceAlarmList> {

    private final Logger logger = LoggerFactory.getLogger(InstanceReferenceAlarmListEsPersistenceDAO.class);

    public InstanceReferenceAlarmListEsPersistenceDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public InstanceReferenceAlarmList get(String id) {
        DocumentResult getResponse = getClient().prepareGet(InstanceReferenceAlarmListTable.TABLE, id);
        if (getResponse.isSucceeded()) {
            InstanceReferenceAlarmList serviceReferenceAlarmList = new InstanceReferenceAlarmList();
            serviceReferenceAlarmList.setId(id);
            JsonObject source = getResponse.getSourceAsObject(JsonObject.class);
            serviceReferenceAlarmList.setFrontApplicationId((source.get(InstanceReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID)).getAsInt());
            serviceReferenceAlarmList.setBehindApplicationId((source.get(InstanceReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID)).getAsInt());
            serviceReferenceAlarmList.setFrontInstanceId((source.get(InstanceReferenceAlarmListTable.COLUMN_FRONT_INSTANCE_ID)).getAsInt());
            serviceReferenceAlarmList.setBehindInstanceId((source.get(InstanceReferenceAlarmListTable.COLUMN_BEHIND_INSTANCE_ID)).getAsInt());
            serviceReferenceAlarmList.setSourceValue((source.get(InstanceReferenceAlarmListTable.COLUMN_SOURCE_VALUE)).getAsInt());

            serviceReferenceAlarmList.setAlarmType((source.get(InstanceReferenceAlarmListTable.COLUMN_ALARM_TYPE)).getAsInt());
            serviceReferenceAlarmList.setAlarmContent(source.get(InstanceReferenceAlarmListTable.COLUMN_ALARM_CONTENT).getAsString());

            serviceReferenceAlarmList.setTimeBucket((source.get(InstanceReferenceAlarmListTable.COLUMN_TIME_BUCKET)).getAsLong());
            return serviceReferenceAlarmList;
        } else {
            return null;
        }
    }

    @Override public Index prepareBatchInsert(InstanceReferenceAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(InstanceReferenceAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(InstanceReferenceAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(InstanceReferenceAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return new Index.Builder(source).index(InstanceReferenceAlarmListTable.TABLE).id(data.getId()).build();
    }

    @Override public Update prepareBatchUpdate(InstanceReferenceAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(InstanceReferenceAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(InstanceReferenceAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(InstanceReferenceAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return new Update.Builder(source).index(InstanceReferenceAlarmListTable.TABLE).id(data.getId()).build();
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(InstanceReferenceAlarmListTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));

        long deleted = getClient().batchDelete(InstanceReferenceAlarmListTable.TABLE, searchSourceBuilder.toString());
        logger.info("Delete {} rows history from {} index.", deleted, InstanceReferenceAlarmListTable.TABLE);
    }
}
