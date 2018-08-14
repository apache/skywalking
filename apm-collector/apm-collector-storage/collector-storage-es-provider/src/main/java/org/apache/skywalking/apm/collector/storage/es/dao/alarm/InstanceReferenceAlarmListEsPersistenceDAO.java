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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.*;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.common.xcontent.*;

/**
 * @author peng-yongsheng
 */
public class InstanceReferenceAlarmListEsPersistenceDAO extends AbstractPersistenceEsDAO<InstanceReferenceAlarmList> implements IInstanceReferenceAlarmListPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, InstanceReferenceAlarmList> {

    public InstanceReferenceAlarmListEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return InstanceReferenceAlarmListTable.TABLE;
    }

    @Override protected InstanceReferenceAlarmList esDataToStreamData(Map<String, Object> source) {
        InstanceReferenceAlarmList serviceReferenceAlarmList = new InstanceReferenceAlarmList();
        serviceReferenceAlarmList.setFrontApplicationId(((Number)source.get(InstanceReferenceAlarmListTable.FRONT_APPLICATION_ID.getName())).intValue());
        serviceReferenceAlarmList.setBehindApplicationId(((Number)source.get(InstanceReferenceAlarmListTable.BEHIND_APPLICATION_ID.getName())).intValue());
        serviceReferenceAlarmList.setFrontInstanceId(((Number)source.get(InstanceReferenceAlarmListTable.FRONT_INSTANCE_ID.getName())).intValue());
        serviceReferenceAlarmList.setBehindInstanceId(((Number)source.get(InstanceReferenceAlarmListTable.BEHIND_INSTANCE_ID.getName())).intValue());
        serviceReferenceAlarmList.setSourceValue(((Number)source.get(InstanceReferenceAlarmListTable.SOURCE_VALUE.getName())).intValue());

        serviceReferenceAlarmList.setAlarmType(((Number)source.get(InstanceReferenceAlarmListTable.ALARM_TYPE.getName())).intValue());
        serviceReferenceAlarmList.setAlarmContent((String)source.get(InstanceReferenceAlarmListTable.ALARM_CONTENT.getName()));

        serviceReferenceAlarmList.setTimeBucket(((Number)source.get(InstanceReferenceAlarmListTable.TIME_BUCKET.getName())).longValue());
        return serviceReferenceAlarmList;
    }

    @Override protected XContentBuilder esStreamDataToEsData(InstanceReferenceAlarmList streamData) throws IOException {
        return XContentFactory.jsonBuilder().startObject()
            .field(InstanceReferenceAlarmListTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId())
            .field(InstanceReferenceAlarmListTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId())
            .field(InstanceReferenceAlarmListTable.FRONT_INSTANCE_ID.getName(), streamData.getFrontInstanceId())
            .field(InstanceReferenceAlarmListTable.BEHIND_INSTANCE_ID.getName(), streamData.getBehindInstanceId())
            .field(InstanceReferenceAlarmListTable.SOURCE_VALUE.getName(), streamData.getSourceValue())

            .field(InstanceReferenceAlarmListTable.ALARM_TYPE.getName(), streamData.getAlarmType())
            .field(InstanceReferenceAlarmListTable.ALARM_CONTENT.getName(), streamData.getAlarmContent())

            .field(InstanceReferenceAlarmListTable.TIME_BUCKET.getName(), streamData.getTimeBucket())
            .endObject();
    }

    @Override protected String timeBucketColumnNameForDelete() {
        return InstanceReferenceAlarmListTable.TIME_BUCKET.getName();
    }

    @GraphComputingMetric(name = "/persistence/get/" + InstanceReferenceAlarmListTable.TABLE)
    @Override public InstanceReferenceAlarmList get(String id) {
        return super.get(id);
    }
}
