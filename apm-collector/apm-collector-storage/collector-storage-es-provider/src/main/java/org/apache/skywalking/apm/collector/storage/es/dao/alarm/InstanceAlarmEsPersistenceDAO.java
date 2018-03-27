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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmTable;
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
public class InstanceAlarmEsPersistenceDAO extends EsDAO implements IInstanceAlarmPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, InstanceAlarm> {

    private final Logger logger = LoggerFactory.getLogger(InstanceAlarmEsPersistenceDAO.class);

    public InstanceAlarmEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public InstanceAlarm get(String id) {
        GetResponse getResponse = getClient().prepareGet(InstanceAlarmTable.TABLE, id).get();
        if (getResponse.isExists()) {
            InstanceAlarm instanceAlarm = new InstanceAlarm();
            instanceAlarm.setId(id);
            Map<String, Object> source = getResponse.getSource();
            instanceAlarm.setApplicationId(((Number) source.get(InstanceAlarmTable.COLUMN_APPLICATION_ID)).intValue());
            instanceAlarm.setInstanceId(((Number) source.get(InstanceAlarmTable.COLUMN_INSTANCE_ID)).intValue());
            instanceAlarm.setSourceValue(((Number) source.get(InstanceAlarmTable.COLUMN_SOURCE_VALUE)).intValue());

            instanceAlarm.setAlarmType(((Number) source.get(InstanceAlarmTable.COLUMN_ALARM_TYPE)).intValue());
            instanceAlarm.setAlarmContent((String) source.get(InstanceAlarmTable.COLUMN_ALARM_CONTENT));

            instanceAlarm.setLastTimeBucket(((Number) source.get(InstanceAlarmTable.COLUMN_LAST_TIME_BUCKET)).longValue());
            return instanceAlarm;
        } else {
            return null;
        }
    }

    @Override
    public IndexRequestBuilder prepareBatchInsert(InstanceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceAlarmTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceAlarmTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(InstanceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(InstanceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(InstanceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(InstanceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return getClient().prepareIndex(InstanceAlarmTable.TABLE, data.getId()).setSource(source);
    }

    @Override
    public UpdateRequestBuilder prepareBatchUpdate(InstanceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceAlarmTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(InstanceAlarmTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(InstanceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(InstanceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(InstanceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(InstanceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return getClient().prepareUpdate(InstanceAlarmTable.TABLE, data.getId()).setDoc(source);
    }

    @Override
    public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete(
                QueryBuilders.rangeQuery(InstanceAlarmTable.COLUMN_LAST_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket),
                InstanceAlarmTable.TABLE)
                .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, InstanceAlarmTable.TABLE);
    }
}
