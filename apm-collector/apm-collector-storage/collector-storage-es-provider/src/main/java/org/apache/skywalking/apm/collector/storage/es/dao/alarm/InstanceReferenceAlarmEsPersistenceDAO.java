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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarmTable;
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
public class InstanceReferenceAlarmEsPersistenceDAO extends EsDAO implements IInstanceReferenceAlarmPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, InstanceReferenceAlarm> {

    private final Logger logger = LoggerFactory.getLogger(InstanceReferenceAlarmEsPersistenceDAO.class);

    public InstanceReferenceAlarmEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public InstanceReferenceAlarm get(String id) {
        GetResponse getResponse = getClient().prepareGet(InstanceReferenceAlarmTable.TABLE, id).get();
        if (getResponse.isExists()) {
            InstanceReferenceAlarm instanceReferenceAlarm = new InstanceReferenceAlarm();
            instanceReferenceAlarm.setId(id);
            Map<String, Object> source = getResponse.getSource();
            instanceReferenceAlarm.setFrontApplicationId(((Number) source.get(InstanceReferenceAlarmTable.FRONT_APPLICATION_ID.getName())).intValue());
            instanceReferenceAlarm.setBehindApplicationId(((Number) source.get(InstanceReferenceAlarmTable.BEHIND_APPLICATION_ID.getName())).intValue());
            instanceReferenceAlarm.setFrontInstanceId(((Number) source.get(InstanceReferenceAlarmTable.FRONT_INSTANCE_ID.getName())).intValue());
            instanceReferenceAlarm.setBehindInstanceId(((Number) source.get(InstanceReferenceAlarmTable.BEHIND_INSTANCE_ID.getName())).intValue());
            instanceReferenceAlarm.setSourceValue(((Number) source.get(InstanceReferenceAlarmTable.SOURCE_VALUE.getName())).intValue());

            instanceReferenceAlarm.setAlarmType(((Number) source.get(InstanceReferenceAlarmTable.ALARM_TYPE.getName())).intValue());
            instanceReferenceAlarm.setAlarmContent((String) source.get(InstanceReferenceAlarmTable.ALARM_CONTENT.getName()));

            instanceReferenceAlarm.setLastTimeBucket(((Number) source.get(InstanceReferenceAlarmTable.LAST_TIME_BUCKET.getName())).longValue());
            return instanceReferenceAlarm;
        } else {
            return null;
        }
    }

    @Override
    public IndexRequestBuilder prepareBatchInsert(InstanceReferenceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceReferenceAlarmTable.FRONT_APPLICATION_ID.getName(), data.getFrontApplicationId());
        source.put(InstanceReferenceAlarmTable.BEHIND_APPLICATION_ID.getName(), data.getBehindApplicationId());
        source.put(InstanceReferenceAlarmTable.FRONT_INSTANCE_ID.getName(), data.getFrontInstanceId());
        source.put(InstanceReferenceAlarmTable.BEHIND_INSTANCE_ID.getName(), data.getBehindInstanceId());
        source.put(InstanceReferenceAlarmTable.SOURCE_VALUE.getName(), data.getSourceValue());

        source.put(InstanceReferenceAlarmTable.ALARM_TYPE.getName(), data.getAlarmType());
        source.put(InstanceReferenceAlarmTable.ALARM_CONTENT.getName(), data.getAlarmContent());

        source.put(InstanceReferenceAlarmTable.LAST_TIME_BUCKET.getName(), data.getLastTimeBucket());

        return getClient().prepareIndex(InstanceReferenceAlarmTable.TABLE, data.getId()).setSource(source);
    }

    @Override
    public UpdateRequestBuilder prepareBatchUpdate(InstanceReferenceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceReferenceAlarmTable.FRONT_APPLICATION_ID.getName(), data.getFrontApplicationId());
        source.put(InstanceReferenceAlarmTable.BEHIND_APPLICATION_ID.getName(), data.getBehindApplicationId());
        source.put(InstanceReferenceAlarmTable.FRONT_INSTANCE_ID.getName(), data.getFrontInstanceId());
        source.put(InstanceReferenceAlarmTable.BEHIND_INSTANCE_ID.getName(), data.getBehindInstanceId());
        source.put(InstanceReferenceAlarmTable.SOURCE_VALUE.getName(), data.getSourceValue());

        source.put(InstanceReferenceAlarmTable.ALARM_TYPE.getName(), data.getAlarmType());
        source.put(InstanceReferenceAlarmTable.ALARM_CONTENT.getName(), data.getAlarmContent());

        source.put(InstanceReferenceAlarmTable.LAST_TIME_BUCKET.getName(), data.getLastTimeBucket());

        return getClient().prepareUpdate(InstanceReferenceAlarmTable.TABLE, data.getId()).setDoc(source);
    }

    @Override
    public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete(
                QueryBuilders.rangeQuery(InstanceReferenceAlarmTable.LAST_TIME_BUCKET.getName()).gte(startTimeBucket).lte(endTimeBucket),
                InstanceReferenceAlarmTable.TABLE)
                .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, InstanceReferenceAlarmTable.TABLE);
    }
}
