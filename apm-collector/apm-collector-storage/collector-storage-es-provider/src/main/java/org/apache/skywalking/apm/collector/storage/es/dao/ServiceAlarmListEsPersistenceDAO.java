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
import org.apache.skywalking.apm.collector.storage.dao.IServiceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarmListTable;
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
public class ServiceAlarmListEsPersistenceDAO extends EsDAO implements IServiceAlarmListPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ServiceAlarmList> {

    private final Logger logger = LoggerFactory.getLogger(ServiceAlarmListEsPersistenceDAO.class);

    public ServiceAlarmListEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override public ServiceAlarmList get(String id) {
        GetResponse getResponse = getClient().prepareGet(ServiceAlarmListTable.TABLE, id).get();
        if (getResponse.isExists()) {
            ServiceAlarmList serviceAlarmList = new ServiceAlarmList();
            serviceAlarmList.setId(id);
            Map<String, Object> source = getResponse.getSource();
            serviceAlarmList.setApplicationId(((Number)source.get(ServiceAlarmListTable.COLUMN_APPLICATION_ID)).intValue());
            serviceAlarmList.setInstanceId(((Number)source.get(ServiceAlarmListTable.COLUMN_INSTANCE_ID)).intValue());
            serviceAlarmList.setServiceId(((Number)source.get(ServiceAlarmListTable.COLUMN_SERVICE_ID)).intValue());
            serviceAlarmList.setSourceValue(((Number)source.get(ServiceAlarmListTable.COLUMN_SOURCE_VALUE)).intValue());

            serviceAlarmList.setAlarmType(((Number)source.get(ServiceAlarmListTable.COLUMN_ALARM_TYPE)).intValue());
            serviceAlarmList.setAlarmContent((String)source.get(ServiceAlarmListTable.COLUMN_ALARM_CONTENT));

            serviceAlarmList.setTimeBucket(((Number)source.get(ServiceAlarmListTable.COLUMN_TIME_BUCKET)).longValue());
            return serviceAlarmList;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(ServiceAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceAlarmListTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ServiceAlarmListTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(ServiceAlarmListTable.COLUMN_SERVICE_ID, data.getServiceId());
        source.put(ServiceAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ServiceAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ServiceAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareIndex(ServiceAlarmListTable.TABLE, data.getId()).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(ServiceAlarmList data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceAlarmListTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ServiceAlarmListTable.COLUMN_INSTANCE_ID, data.getInstanceId());
        source.put(ServiceAlarmListTable.COLUMN_SERVICE_ID, data.getServiceId());
        source.put(ServiceAlarmListTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ServiceAlarmListTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ServiceAlarmListTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ServiceAlarmListTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        return getClient().prepareUpdate(ServiceAlarmListTable.TABLE, data.getId()).setDoc(source);
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete()
            .filter(QueryBuilders.rangeQuery(ServiceAlarmListTable.COLUMN_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket))
            .source(ServiceAlarmListTable.TABLE)
            .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, ServiceAlarmListTable.TABLE);
    }
}
