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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmListTable;
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
public class InstanceAlarmListEsPersistenceDAO extends EsHttpDAO implements IInstanceAlarmListPersistenceDAO<Index, Update, InstanceAlarmList> {

    private final Logger logger = LoggerFactory.getLogger(InstanceAlarmListEsPersistenceDAO.class);

    public InstanceAlarmListEsPersistenceDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public InstanceAlarmList get(String id) {
        DocumentResult getResponse = getClient().prepareGet(InstanceAlarmListTable.TABLE, id);
        if (getResponse.isSucceeded()) {
            InstanceAlarmList instanceAlarmList = new InstanceAlarmList();
            instanceAlarmList.setId(id);
            JsonObject source = getResponse.getSourceAsObject(JsonObject.class);
            instanceAlarmList.setApplicationId((source.get(InstanceAlarmListTable.COLUMN_APPLICATION_ID)).getAsInt());
            instanceAlarmList.setInstanceId((source.get(InstanceAlarmListTable.COLUMN_INSTANCE_ID)).getAsInt());
            instanceAlarmList.setSourceValue((source.get(InstanceAlarmListTable.COLUMN_SOURCE_VALUE)).getAsInt());

            instanceAlarmList.setAlarmType((source.get(InstanceAlarmListTable.COLUMN_ALARM_TYPE)).getAsInt());
            instanceAlarmList.setAlarmContent(source.get(InstanceAlarmListTable.COLUMN_ALARM_CONTENT).getAsString());

            instanceAlarmList.setTimeBucket((source.get(InstanceAlarmListTable.COLUMN_TIME_BUCKET)).getAsLong());
            return instanceAlarmList;
        } else {
            return null;
        }
    }

    @Override public Index prepareBatchInsert(InstanceAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceAlarmListTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceAlarmListTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(InstanceAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(InstanceAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(InstanceAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(InstanceAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return new Index.Builder(source).index(InstanceAlarmListTable.TABLE).id(data.getId()).build();
    }

    @Override public Update prepareBatchUpdate(InstanceAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceAlarmListTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceAlarmListTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(InstanceAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(InstanceAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(InstanceAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(InstanceAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return new Update.Builder(source).index(InstanceAlarmListTable.TABLE).id(data.getId()).build();
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(InstanceAlarmListTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));

        long deleted = getClient().batchDelete(InstanceAlarmListTable.TABLE, searchSourceBuilder.toString());
        logger.info("Delete {} rows history from {} index.", deleted, InstanceAlarmListTable.TABLE);
    }
}
