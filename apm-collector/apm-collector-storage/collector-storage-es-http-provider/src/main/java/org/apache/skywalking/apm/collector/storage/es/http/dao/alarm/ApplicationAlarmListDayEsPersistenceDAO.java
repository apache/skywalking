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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmListDayPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsHttpDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmListTable;
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
public class ApplicationAlarmListDayEsPersistenceDAO extends EsHttpDAO implements IApplicationAlarmListDayPersistenceDAO<Index, Update, ApplicationAlarmList> {

    private final Logger logger = LoggerFactory.getLogger(ApplicationAlarmListDayEsPersistenceDAO.class);

    public ApplicationAlarmListDayEsPersistenceDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public ApplicationAlarmList get(String id) {
        DocumentResult getResponse = getClient().prepareGet(ApplicationAlarmListTable.TABLE, id);
        if (getResponse.isSucceeded()) {
            ApplicationAlarmList applicationAlarmList = new ApplicationAlarmList();
            applicationAlarmList.setId(id);
            JsonObject source = getResponse.getSourceAsObject(JsonObject.class);
            applicationAlarmList.setApplicationId((source.get(ApplicationAlarmListTable.COLUMN_APPLICATION_ID)).getAsInt());
            applicationAlarmList.setSourceValue((source.get(ApplicationAlarmListTable.COLUMN_SOURCE_VALUE)).getAsInt());

            applicationAlarmList.setAlarmType((source.get(ApplicationAlarmListTable.COLUMN_ALARM_TYPE)).getAsInt());
            applicationAlarmList.setAlarmContent(source.get(ApplicationAlarmListTable.COLUMN_ALARM_CONTENT).getAsString());

            applicationAlarmList.setTimeBucket((source.get(ApplicationAlarmListTable.COLUMN_TIME_BUCKET)).getAsLong());
            return applicationAlarmList;
        } else {
            return null;
        }
    }

    @Override public Index prepareBatchInsert(ApplicationAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationAlarmListTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ApplicationAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ApplicationAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ApplicationAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ApplicationAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return new Index.Builder(source).index(ApplicationAlarmListTable.TABLE).type(ApplicationAlarmListTable.TABLE_TYPE).id(data.getId()).build();
    }

    @Override public Update prepareBatchUpdate(ApplicationAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationAlarmListTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ApplicationAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ApplicationAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ApplicationAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ApplicationAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        Map<String, Object> doc = new HashMap<>();
        doc.put("doc", source);
        
        return new Update.Builder(doc).index(ApplicationAlarmListTable.TABLE).type(ApplicationAlarmListTable.TABLE_TYPE).id(data.getId()).build();
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        
        SearchSourceBuilder searchSourceBuilder = new SearchSourceBuilder();
        searchSourceBuilder.query(QueryBuilders.rangeQuery(ApplicationAlarmListTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket));

        long deleted = getClient().batchDelete(ApplicationAlarmListTable.TABLE, searchSourceBuilder.toString());
        logger.info("Delete {} rows history from {} index.", deleted, ApplicationAlarmListTable.TABLE);
    }
}
