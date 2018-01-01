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

package org.apache.skywalking.apm.collector.storage.es.dao;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.IInstanceReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarmListTable;
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
public class InstanceReferenceAlarmListEsPersistenceDAO extends EsDAO implements IInstanceReferenceAlarmListPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, InstanceReferenceAlarmList> {

    private final Logger logger = LoggerFactory.getLogger(InstanceReferenceAlarmListEsPersistenceDAO.class);

    public InstanceReferenceAlarmListEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public InstanceReferenceAlarmList get(String id) {
        GetResponse getResponse = getClient().prepareGet(InstanceReferenceAlarmListTable.TABLE, id).get();
        if (getResponse.isExists()) {
            InstanceReferenceAlarmList serviceReferenceAlarmList = new InstanceReferenceAlarmList(id);
            Map<String, Object> source = getResponse.getSource();
            serviceReferenceAlarmList.setFrontApplicationId(((Number)source.get(InstanceReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID)).intValue());
            serviceReferenceAlarmList.setBehindApplicationId(((Number)source.get(InstanceReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID)).intValue());
            serviceReferenceAlarmList.setFrontInstanceId(((Number)source.get(InstanceReferenceAlarmListTable.COLUMN_FRONT_INSTANCE_ID)).intValue());
            serviceReferenceAlarmList.setBehindInstanceId(((Number)source.get(InstanceReferenceAlarmListTable.COLUMN_BEHIND_INSTANCE_ID)).intValue());
            serviceReferenceAlarmList.setSourceValue(((Number)source.get(InstanceReferenceAlarmListTable.COLUMN_SOURCE_VALUE)).intValue());

            serviceReferenceAlarmList.setAlarmType(((Number)source.get(InstanceReferenceAlarmListTable.COLUMN_ALARM_TYPE)).intValue());
            serviceReferenceAlarmList.setAlarmContent((String)source.get(InstanceReferenceAlarmListTable.COLUMN_ALARM_CONTENT));

            serviceReferenceAlarmList.setTimeBucket(((Number)source.get(InstanceReferenceAlarmListTable.COLUMN_TIME_BUCKET)).longValue());
            return serviceReferenceAlarmList;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(InstanceReferenceAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(InstanceReferenceAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(InstanceReferenceAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(InstanceReferenceAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(InstanceReferenceAlarmListTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(InstanceReferenceAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(InstanceReferenceAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(InstanceReferenceAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(InstanceReferenceAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(InstanceReferenceAlarmListTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(InstanceReferenceAlarmListTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(InstanceReferenceAlarmListTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, InstanceReferenceAlarmListTable.TABLE);
    }
}
