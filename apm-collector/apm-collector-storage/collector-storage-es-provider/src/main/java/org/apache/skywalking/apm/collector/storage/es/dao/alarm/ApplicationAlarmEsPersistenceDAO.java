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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.*;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.common.xcontent.*;

/**
 * @author peng-yongsheng
 */
public class ApplicationAlarmEsPersistenceDAO extends AbstractPersistenceEsDAO<ApplicationAlarm> implements IApplicationAlarmPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ApplicationAlarm> {

    public ApplicationAlarmEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return ApplicationAlarmTable.TABLE;
    }

    @Override protected ApplicationAlarm esDataToStreamData(Map<String, Object> source) {
        ApplicationAlarm instanceAlarm = new ApplicationAlarm();
        instanceAlarm.setApplicationId(((Number)source.get(ApplicationAlarmTable.APPLICATION_ID.getName())).intValue());
        instanceAlarm.setSourceValue(((Number)source.get(ApplicationAlarmTable.SOURCE_VALUE.getName())).intValue());

        instanceAlarm.setAlarmType(((Number)source.get(ApplicationAlarmTable.ALARM_TYPE.getName())).intValue());
        instanceAlarm.setAlarmContent((String)source.get(ApplicationAlarmTable.ALARM_CONTENT.getName()));

        instanceAlarm.setLastTimeBucket(((Number)source.get(ApplicationAlarmTable.LAST_TIME_BUCKET.getName())).longValue());
        return instanceAlarm;
    }

    @Override protected XContentBuilder esStreamDataToEsData(ApplicationAlarm streamData) throws IOException {
        return XContentFactory.jsonBuilder().startObject()
            .field(ApplicationAlarmTable.APPLICATION_ID.getName(), streamData.getApplicationId())
            .field(ApplicationAlarmTable.SOURCE_VALUE.getName(), streamData.getSourceValue())

            .field(ApplicationAlarmTable.ALARM_TYPE.getName(), streamData.getAlarmType())
            .field(ApplicationAlarmTable.ALARM_CONTENT.getName(), streamData.getAlarmContent())

            .field(ApplicationAlarmTable.LAST_TIME_BUCKET.getName(), streamData.getLastTimeBucket())
            .endObject();
    }

    @Override protected String timeBucketColumnNameForDelete() {
        return ApplicationAlarmTable.LAST_TIME_BUCKET.getName();
    }

    @GraphComputingMetric(name = "/persistence/get/" + ApplicationAlarmTable.TABLE)
    @Override public ApplicationAlarm get(String id) {
        return super.get(id);
    }
}
