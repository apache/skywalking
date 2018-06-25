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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.*;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.common.xcontent.*;

/**
 * @author peng-yongsheng
 */
public class InstanceAlarmListEsPersistenceDAO extends AbstractPersistenceEsDAO<InstanceAlarmList> implements IInstanceAlarmListPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, InstanceAlarmList> {

    public InstanceAlarmListEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return InstanceAlarmListTable.TABLE;
    }

    @Override protected InstanceAlarmList esDataToStreamData(Map<String, Object> source) {
        InstanceAlarmList instanceAlarmList = new InstanceAlarmList();
        instanceAlarmList.setApplicationId(((Number)source.get(InstanceAlarmListTable.APPLICATION_ID.getName())).intValue());
        instanceAlarmList.setInstanceId(((Number)source.get(InstanceAlarmListTable.INSTANCE_ID.getName())).intValue());
        instanceAlarmList.setSourceValue(((Number)source.get(InstanceAlarmListTable.SOURCE_VALUE.getName())).intValue());

        instanceAlarmList.setAlarmType(((Number)source.get(InstanceAlarmListTable.ALARM_TYPE.getName())).intValue());
        instanceAlarmList.setAlarmContent((String)source.get(InstanceAlarmListTable.ALARM_CONTENT.getName()));

        instanceAlarmList.setTimeBucket(((Number)source.get(InstanceAlarmListTable.TIME_BUCKET.getName())).longValue());
        return instanceAlarmList;
    }

    @Override protected XContentBuilder esStreamDataToEsData(InstanceAlarmList streamData) throws IOException {
        return XContentFactory.jsonBuilder().startObject()
            .field(InstanceAlarmListTable.APPLICATION_ID.getName(), streamData.getApplicationId())
            .field(InstanceAlarmListTable.INSTANCE_ID.getName(), streamData.getInstanceId())
            .field(InstanceAlarmListTable.SOURCE_VALUE.getName(), streamData.getSourceValue())

            .field(InstanceAlarmListTable.ALARM_TYPE.getName(), streamData.getAlarmType())
            .field(InstanceAlarmListTable.ALARM_CONTENT.getName(), streamData.getAlarmContent())

            .field(InstanceAlarmListTable.TIME_BUCKET.getName(), streamData.getTimeBucket())
            .endObject();
    }

    @Override protected String timeBucketColumnNameForDelete() {
        return InstanceAlarmListTable.TIME_BUCKET.getName();
    }

    @GraphComputingMetric(name = "/persistence/get/" + InstanceAlarmListTable.TABLE)
    @Override public InstanceAlarmList get(String id) {
        return super.get(id);
    }
}
