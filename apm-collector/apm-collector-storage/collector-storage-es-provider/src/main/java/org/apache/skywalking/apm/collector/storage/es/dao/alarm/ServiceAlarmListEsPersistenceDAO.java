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
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarmListTable;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;

/**
 * @author peng-yongsheng
 */
public class ServiceAlarmListEsPersistenceDAO extends AbstractPersistenceEsDAO<ServiceAlarmList> implements IServiceAlarmListPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ServiceAlarmList> {

    public ServiceAlarmListEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return ServiceAlarmListTable.TABLE;
    }

    @Override protected ServiceAlarmList esDataToStreamData(Map<String, Object> source) {
        ServiceAlarmList serviceAlarmList = new ServiceAlarmList();
        serviceAlarmList.setApplicationId(((Number)source.get(ServiceAlarmListTable.APPLICATION_ID.getName())).intValue());
        serviceAlarmList.setInstanceId(((Number)source.get(ServiceAlarmListTable.INSTANCE_ID.getName())).intValue());
        serviceAlarmList.setServiceId(((Number)source.get(ServiceAlarmListTable.SERVICE_ID.getName())).intValue());
        serviceAlarmList.setSourceValue(((Number)source.get(ServiceAlarmListTable.SOURCE_VALUE.getName())).intValue());

        serviceAlarmList.setAlarmType(((Number)source.get(ServiceAlarmListTable.ALARM_TYPE.getName())).intValue());
        serviceAlarmList.setAlarmContent((String)source.get(ServiceAlarmListTable.ALARM_CONTENT.getName()));

        serviceAlarmList.setTimeBucket(((Number)source.get(ServiceAlarmListTable.TIME_BUCKET.getName())).longValue());
        return serviceAlarmList;
    }

    @Override protected Map<String, Object> esStreamDataToEsData(ServiceAlarmList streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ServiceAlarmListTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        target.put(ServiceAlarmListTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(ServiceAlarmListTable.SERVICE_ID.getName(), streamData.getServiceId());
        target.put(ServiceAlarmListTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        target.put(ServiceAlarmListTable.ALARM_TYPE.getName(), streamData.getAlarmType());
        target.put(ServiceAlarmListTable.ALARM_CONTENT.getName(), streamData.getAlarmContent());

        target.put(ServiceAlarmListTable.TIME_BUCKET.getName(), streamData.getTimeBucket());
        return target;
    }

    @Override protected String timeBucketColumnNameForDelete() {
        return ServiceAlarmListTable.TIME_BUCKET.getName();
    }

    @GraphComputingMetric(name = "/persistence/get/" + ServiceAlarmListTable.TABLE)
    @Override public ServiceAlarmList get(String id) {
        return super.get(id);
    }
}
