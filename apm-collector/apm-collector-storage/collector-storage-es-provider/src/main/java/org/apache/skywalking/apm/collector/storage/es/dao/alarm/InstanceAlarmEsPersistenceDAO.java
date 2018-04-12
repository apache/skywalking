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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmTable;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;

/**
 * @author peng-yongsheng
 */
public class InstanceAlarmEsPersistenceDAO extends AbstractPersistenceEsDAO<InstanceAlarm> implements IInstanceAlarmPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, InstanceAlarm> {

    public InstanceAlarmEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return InstanceAlarmTable.TABLE;
    }

    @Override protected InstanceAlarm esDataToStreamData(Map<String, Object> source) {
        InstanceAlarm instanceAlarm = new InstanceAlarm();
        instanceAlarm.setApplicationId(((Number)source.get(InstanceAlarmTable.APPLICATION_ID.getName())).intValue());
        instanceAlarm.setInstanceId(((Number)source.get(InstanceAlarmTable.INSTANCE_ID.getName())).intValue());
        instanceAlarm.setSourceValue(((Number)source.get(InstanceAlarmTable.SOURCE_VALUE.getName())).intValue());

        instanceAlarm.setAlarmType(((Number)source.get(InstanceAlarmTable.ALARM_TYPE.getName())).intValue());
        instanceAlarm.setAlarmContent((String)source.get(InstanceAlarmTable.ALARM_CONTENT.getName()));

        instanceAlarm.setLastTimeBucket(((Number)source.get(InstanceAlarmTable.LAST_TIME_BUCKET.getName())).longValue());
        return instanceAlarm;
    }

    @Override protected Map<String, Object> esStreamDataToEsData(InstanceAlarm streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(InstanceAlarmTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        target.put(InstanceAlarmTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(InstanceAlarmTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        target.put(InstanceAlarmTable.ALARM_TYPE.getName(), streamData.getAlarmType());
        target.put(InstanceAlarmTable.ALARM_CONTENT.getName(), streamData.getAlarmContent());

        target.put(InstanceAlarmTable.LAST_TIME_BUCKET.getName(), streamData.getLastTimeBucket());
        return target;
    }

    @Override protected String timeBucketColumnNameForDelete() {
        return InstanceAlarmTable.LAST_TIME_BUCKET.getName();
    }

    @GraphComputingMetric(name = "/persistence/get/" + InstanceAlarmTable.TABLE)
    @Override public InstanceAlarm get(String id) {
        return super.get(id);
    }
}
