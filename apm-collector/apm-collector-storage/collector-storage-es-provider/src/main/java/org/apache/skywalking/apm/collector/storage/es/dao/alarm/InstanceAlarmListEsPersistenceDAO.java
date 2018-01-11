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

package org.apache.skywalking.apm.collector.storage.es.dao.alarm;

import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmListTable;
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
public class InstanceAlarmListEsPersistenceDAO extends EsDAO implements IInstanceAlarmListPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, InstanceAlarmList> {

    private final Logger logger = LoggerFactory.getLogger(InstanceAlarmListEsPersistenceDAO.class);

    public InstanceAlarmListEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public InstanceAlarmList get(String id) {
        GetResponse getResponse = getClient().prepareGet(InstanceAlarmListTable.TABLE, id).get();
        if (getResponse.isExists()) {
            InstanceAlarmList instanceAlarmList = new InstanceAlarmList();
            instanceAlarmList.setId(id);
            Map<String, Object> source = getResponse.getSource();
            instanceAlarmList.setApplicationId(((Number)source.get(InstanceAlarmListTable.COLUMN_APPLICATION_ID)).intValue());
            instanceAlarmList.setInstanceId(((Number)source.get(InstanceAlarmListTable.COLUMN_INSTANCE_ID)).intValue());
            instanceAlarmList.setSourceValue(((Number)source.get(InstanceAlarmListTable.COLUMN_SOURCE_VALUE)).intValue());

            instanceAlarmList.setAlarmType(((Number)source.get(InstanceAlarmListTable.COLUMN_ALARM_TYPE)).intValue());
            instanceAlarmList.setAlarmContent((String)source.get(InstanceAlarmListTable.COLUMN_ALARM_CONTENT));

            instanceAlarmList.setTimeBucket(((Number)source.get(InstanceAlarmListTable.COLUMN_TIME_BUCKET)).longValue());
            return instanceAlarmList;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(InstanceAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceAlarmListTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceAlarmListTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(InstanceAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(InstanceAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(InstanceAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(InstanceAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(InstanceAlarmListTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(InstanceAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceAlarmListTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceAlarmListTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(InstanceAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(InstanceAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(InstanceAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(InstanceAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(InstanceAlarmListTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(InstanceAlarmListTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(InstanceAlarmListTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, InstanceAlarmListTable.TABLE);
    }
}
