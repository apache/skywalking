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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarmTable;
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
public class ServiceAlarmEsPersistenceDAO extends EsDAO implements IServiceAlarmPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ServiceAlarm> {

    private final Logger logger = LoggerFactory.getLogger(ServiceAlarmEsPersistenceDAO.class);

    public ServiceAlarmEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public ServiceAlarm get(String id) {
        GetResponse getResponse = getClient().prepareGet(ServiceAlarmTable.TABLE, id).get();
        if (getResponse.isExists()) {
            ServiceAlarm serviceAlarm = new ServiceAlarm();
            serviceAlarm.setId(id);
            Map<String, Object> source = getResponse.getSource();
            serviceAlarm.setApplicationId(((Number) source.get(ServiceAlarmTable.COLUMN_APPLICATION_ID)).intValue());
            serviceAlarm.setInstanceId(((Number) source.get(ServiceAlarmTable.COLUMN_INSTANCE_ID)).intValue());
            serviceAlarm.setServiceId(((Number) source.get(ServiceAlarmTable.COLUMN_SERVICE_ID)).intValue());
            serviceAlarm.setSourceValue(((Number) source.get(ServiceAlarmTable.COLUMN_SOURCE_VALUE)).intValue());

            serviceAlarm.setAlarmType(((Number) source.get(ServiceAlarmTable.COLUMN_ALARM_TYPE)).intValue());
            serviceAlarm.setAlarmContent((String) source.get(ServiceAlarmTable.COLUMN_ALARM_CONTENT));

            serviceAlarm.setLastTimeBucket(((Number) source.get(ServiceAlarmTable.COLUMN_LAST_TIME_BUCKET)).longValue());
            return serviceAlarm;
        } else {
            return null;
        }
    }

    @Override
    public IndexRequestBuilder prepareBatchInsert(ServiceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceAlarmTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ServiceAlarmTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(ServiceAlarmTable.COLUMN_SERVICE_ID, data.getServiceId());
        source.put(ServiceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ServiceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ServiceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return getClient().prepareIndex(ServiceAlarmTable.TABLE, data.getId()).setSource(source);
    }

    @Override
    public UpdateRequestBuilder prepareBatchUpdate(ServiceAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceAlarmTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ServiceAlarmTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(ServiceAlarmTable.COLUMN_SERVICE_ID, data.getServiceId());
        source.put(ServiceAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ServiceAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ServiceAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return getClient().prepareUpdate(ServiceAlarmTable.TABLE, data.getId()).setDoc(source);
    }

    @Override
    public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete(
                QueryBuilders.rangeQuery(ServiceAlarmTable.COLUMN_LAST_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket),
                ServiceAlarmTable.TABLE)
                .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, ServiceAlarmTable.TABLE);
    }
}
