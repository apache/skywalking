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
import org.apache.skywalking.apm.collector.storage.dao.IServiceReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmTable;
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
public class ServiceReferenceAlarmEsPersistenceDAO extends EsDAO implements IServiceReferenceAlarmPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ServiceReferenceAlarm> {

    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceAlarmEsPersistenceDAO.class);

    public ServiceReferenceAlarmEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public ServiceReferenceAlarm get(String id) {
        GetResponse getResponse = getClient().prepareGet(ServiceReferenceAlarmTable.TABLE, id).get();
        if (getResponse.isExists()) {
            ServiceReferenceAlarm serviceReferenceAlarm = new ServiceReferenceAlarm(id);
            Map<String, Object> source = getResponse.getSource();
            serviceReferenceAlarm.setFrontApplicationId(((Number)source.get(ServiceReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID)).intValue());
            serviceReferenceAlarm.setBehindApplicationId(((Number)source.get(ServiceReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID)).intValue());
            serviceReferenceAlarm.setFrontInstanceId(((Number)source.get(ServiceReferenceAlarmTable.COLUMN_FRONT_INSTANCE_ID)).intValue());
            serviceReferenceAlarm.setBehindInstanceId(((Number)source.get(ServiceReferenceAlarmTable.COLUMN_BEHIND_INSTANCE_ID)).intValue());
            serviceReferenceAlarm.setFrontServiceId(((Number)source.get(ServiceReferenceAlarmTable.COLUMN_FRONT_SERVICE_ID)).intValue());
            serviceReferenceAlarm.setBehindServiceId(((Number)source.get(ServiceReferenceAlarmTable.COLUMN_BEHIND_SERVICE_ID)).intValue());
            serviceReferenceAlarm.setSourceValue(((Number)source.get(ServiceReferenceAlarmTable.COLUMN_SOURCE_VALUE)).intValue());

            serviceReferenceAlarm.setAlarmType(((Number)source.get(ServiceReferenceAlarmTable.COLUMN_ALARM_TYPE)).intValue());
            serviceReferenceAlarm.setAlarmContent((String)source.get(ServiceReferenceAlarmTable.COLUMN_ALARM_CONTENT));

            serviceReferenceAlarm.setLastTimeBucket(((Number)source.get(ServiceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET)).longValue());
            return serviceReferenceAlarm;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(ServiceReferenceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_SERVICE_ID, data.getFrontServiceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_SERVICE_ID, data.getBehindServiceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceReferenceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ServiceReferenceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ServiceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return getClient().prepareIndex(ServiceReferenceAlarmTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(ServiceReferenceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_INSTANCE_ID, data.getFrontInstanceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_INSTANCE_ID, data.getBehindInstanceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_SERVICE_ID, data.getFrontServiceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_SERVICE_ID, data.getBehindServiceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceReferenceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ServiceReferenceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ServiceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return getClient().prepareUpdate(ServiceReferenceAlarmTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(ServiceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(ServiceReferenceAlarmTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, ServiceReferenceAlarmTable.TABLE);
    }
}
