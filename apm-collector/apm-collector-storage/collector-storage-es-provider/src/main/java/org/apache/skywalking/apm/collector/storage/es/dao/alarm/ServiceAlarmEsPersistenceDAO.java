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

import java.io.IOException;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.*;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.common.xcontent.*;

/**
 * @author peng-yongsheng
 */
public class ServiceAlarmEsPersistenceDAO extends AbstractPersistenceEsDAO<ServiceAlarm> implements IServiceAlarmPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ServiceAlarm> {

    public ServiceAlarmEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return ServiceAlarmTable.TABLE;
    }

    @Override protected ServiceAlarm esDataToStreamData(Map<String, Object> source) {
        ServiceAlarm serviceAlarm = new ServiceAlarm();
        serviceAlarm.setApplicationId(((Number)source.get(ServiceAlarmTable.APPLICATION_ID.getName())).intValue());
        serviceAlarm.setInstanceId(((Number)source.get(ServiceAlarmTable.INSTANCE_ID.getName())).intValue());
        serviceAlarm.setServiceId(((Number)source.get(ServiceAlarmTable.SERVICE_ID.getName())).intValue());
        serviceAlarm.setSourceValue(((Number)source.get(ServiceAlarmTable.SOURCE_VALUE.getName())).intValue());

        serviceAlarm.setAlarmType(((Number)source.get(ServiceAlarmTable.ALARM_TYPE.getName())).intValue());
        serviceAlarm.setAlarmContent((String)source.get(ServiceAlarmTable.ALARM_CONTENT.getName()));

        serviceAlarm.setLastTimeBucket(((Number)source.get(ServiceAlarmTable.LAST_TIME_BUCKET.getName())).longValue());
        return serviceAlarm;
    }

    @Override protected XContentBuilder esStreamDataToEsData(ServiceAlarm streamData) throws IOException {
        return XContentFactory.jsonBuilder().startObject()
            .field(ServiceAlarmTable.APPLICATION_ID.getName(), streamData.getApplicationId())
            .field(ServiceAlarmTable.INSTANCE_ID.getName(), streamData.getInstanceId())
            .field(ServiceAlarmTable.SERVICE_ID.getName(), streamData.getServiceId())
            .field(ServiceAlarmTable.SOURCE_VALUE.getName(), streamData.getSourceValue())

            .field(ServiceAlarmTable.ALARM_TYPE.getName(), streamData.getAlarmType())
            .field(ServiceAlarmTable.ALARM_CONTENT.getName(), streamData.getAlarmContent())

            .field(ServiceAlarmTable.LAST_TIME_BUCKET.getName(), streamData.getLastTimeBucket())
            .endObject();
    }

    @Override protected String timeBucketColumnNameForDelete() {
        return ServiceAlarmTable.LAST_TIME_BUCKET.getName();
    }

    @GraphComputingMetric(name = "/persistence/get/" + ServiceAlarmTable.TABLE)
    @Override public ServiceAlarm get(String id) {
        return super.get(id);
    }
}
