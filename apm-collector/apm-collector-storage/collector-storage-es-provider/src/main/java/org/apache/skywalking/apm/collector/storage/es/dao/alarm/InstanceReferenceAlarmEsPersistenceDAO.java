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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.*;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.common.xcontent.*;

/**
 * @author peng-yongsheng
 */
public class InstanceReferenceAlarmEsPersistenceDAO extends AbstractPersistenceEsDAO<InstanceReferenceAlarm> implements IInstanceReferenceAlarmPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, InstanceReferenceAlarm> {

    public InstanceReferenceAlarmEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return InstanceReferenceAlarmTable.TABLE;
    }

    @Override protected InstanceReferenceAlarm esDataToStreamData(Map<String, Object> source) {
        InstanceReferenceAlarm instanceReferenceAlarm = new InstanceReferenceAlarm();
        instanceReferenceAlarm.setFrontApplicationId(((Number)source.get(InstanceReferenceAlarmTable.FRONT_APPLICATION_ID.getName())).intValue());
        instanceReferenceAlarm.setBehindApplicationId(((Number)source.get(InstanceReferenceAlarmTable.BEHIND_APPLICATION_ID.getName())).intValue());
        instanceReferenceAlarm.setFrontInstanceId(((Number)source.get(InstanceReferenceAlarmTable.FRONT_INSTANCE_ID.getName())).intValue());
        instanceReferenceAlarm.setBehindInstanceId(((Number)source.get(InstanceReferenceAlarmTable.BEHIND_INSTANCE_ID.getName())).intValue());
        instanceReferenceAlarm.setSourceValue(((Number)source.get(InstanceReferenceAlarmTable.SOURCE_VALUE.getName())).intValue());

        instanceReferenceAlarm.setAlarmType(((Number)source.get(InstanceReferenceAlarmTable.ALARM_TYPE.getName())).intValue());
        instanceReferenceAlarm.setAlarmContent((String)source.get(InstanceReferenceAlarmTable.ALARM_CONTENT.getName()));

        instanceReferenceAlarm.setLastTimeBucket(((Number)source.get(InstanceReferenceAlarmTable.LAST_TIME_BUCKET.getName())).longValue());
        return instanceReferenceAlarm;
    }

    @Override protected XContentBuilder esStreamDataToEsData(InstanceReferenceAlarm streamData) throws IOException {
        return XContentFactory.jsonBuilder().startObject()
            .field(InstanceReferenceAlarmTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId())
            .field(InstanceReferenceAlarmTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId())
            .field(InstanceReferenceAlarmTable.FRONT_INSTANCE_ID.getName(), streamData.getFrontInstanceId())
            .field(InstanceReferenceAlarmTable.BEHIND_INSTANCE_ID.getName(), streamData.getBehindInstanceId())
            .field(InstanceReferenceAlarmTable.SOURCE_VALUE.getName(), streamData.getSourceValue())

            .field(InstanceReferenceAlarmTable.ALARM_TYPE.getName(), streamData.getAlarmType())
            .field(InstanceReferenceAlarmTable.ALARM_CONTENT.getName(), streamData.getAlarmContent())

            .field(InstanceReferenceAlarmTable.LAST_TIME_BUCKET.getName(), streamData.getLastTimeBucket())
            .endObject();
    }

    @Override protected String timeBucketColumnNameForDelete() {
        return InstanceReferenceAlarmTable.LAST_TIME_BUCKET.getName();
    }

    @GraphComputingMetric(name = "/persistence/get/" + InstanceReferenceAlarmTable.TABLE)
    @Override public InstanceReferenceAlarm get(String id) {
        return super.get(id);
    }
}
