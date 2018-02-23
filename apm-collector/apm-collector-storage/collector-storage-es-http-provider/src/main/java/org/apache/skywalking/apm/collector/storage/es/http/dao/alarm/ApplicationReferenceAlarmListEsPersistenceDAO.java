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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.http.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarmListTable;
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
public class ApplicationReferenceAlarmListEsPersistenceDAO extends EsDAO implements IApplicationReferenceAlarmListPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ApplicationReferenceAlarmList> {

    private final Logger logger = LoggerFactory.getLogger(ApplicationReferenceAlarmListEsPersistenceDAO.class);

    public ApplicationReferenceAlarmListEsPersistenceDAO(ElasticSearchHttpClient client) {
        super(client);
    }

    @Override public ApplicationReferenceAlarmList get(String id) {
        GetResponse getResponse = getClient().prepareGet(ApplicationReferenceAlarmListTable.TABLE, id).get();
        if (getResponse.isExists()) {
            ApplicationReferenceAlarmList applicationReferenceAlarmList = new ApplicationReferenceAlarmList();
            applicationReferenceAlarmList.setId(id);
            Map<String, Object> source = getResponse.getSource();
            applicationReferenceAlarmList.setFrontApplicationId(((Number)source.get(ApplicationReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID)).intValue());
            applicationReferenceAlarmList.setBehindApplicationId(((Number)source.get(ApplicationReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID)).intValue());
            applicationReferenceAlarmList.setSourceValue(((Number)source.get(ApplicationReferenceAlarmListTable.COLUMN_SOURCE_VALUE)).intValue());

            applicationReferenceAlarmList.setAlarmType(((Number)source.get(ApplicationReferenceAlarmListTable.COLUMN_ALARM_TYPE)).intValue());
            applicationReferenceAlarmList.setAlarmContent((String)source.get(ApplicationReferenceAlarmListTable.COLUMN_ALARM_CONTENT));

            applicationReferenceAlarmList.setTimeBucket(((Number)source.get(ApplicationReferenceAlarmListTable.COLUMN_TIME_BUCKET)).longValue());
            return applicationReferenceAlarmList;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(ApplicationReferenceAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ApplicationReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(ApplicationReferenceAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ApplicationReferenceAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ApplicationReferenceAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ApplicationReferenceAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(ApplicationReferenceAlarmListTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(ApplicationReferenceAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ApplicationReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(ApplicationReferenceAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ApplicationReferenceAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ApplicationReferenceAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ApplicationReferenceAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(ApplicationReferenceAlarmListTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(ApplicationReferenceAlarmListTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(ApplicationReferenceAlarmListTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, ApplicationReferenceAlarmListTable.TABLE);
    }
}
