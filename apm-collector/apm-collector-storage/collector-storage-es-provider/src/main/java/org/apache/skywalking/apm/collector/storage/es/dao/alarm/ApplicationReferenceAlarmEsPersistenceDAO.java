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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.*;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.common.xcontent.*;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceAlarmEsPersistenceDAO extends AbstractPersistenceEsDAO<ApplicationReferenceAlarm> implements IApplicationReferenceAlarmPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ApplicationReferenceAlarm> {

    public ApplicationReferenceAlarmEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return ApplicationReferenceAlarmTable.TABLE;
    }

    @Override protected ApplicationReferenceAlarm esDataToStreamData(Map<String, Object> source) {
        ApplicationReferenceAlarm applicationReferenceAlarm = new ApplicationReferenceAlarm();
        applicationReferenceAlarm.setFrontApplicationId(((Number)source.get(ApplicationReferenceAlarmTable.FRONT_APPLICATION_ID.getName())).intValue());
        applicationReferenceAlarm.setBehindApplicationId(((Number)source.get(ApplicationReferenceAlarmTable.BEHIND_APPLICATION_ID.getName())).intValue());
        applicationReferenceAlarm.setSourceValue(((Number)source.get(ApplicationReferenceAlarmTable.SOURCE_VALUE.getName())).intValue());

        applicationReferenceAlarm.setAlarmType(((Number)source.get(ApplicationReferenceAlarmTable.ALARM_TYPE.getName())).intValue());
        applicationReferenceAlarm.setAlarmContent((String)source.get(ApplicationReferenceAlarmTable.ALARM_CONTENT.getName()));

        applicationReferenceAlarm.setLastTimeBucket(((Number)source.get(ApplicationReferenceAlarmTable.LAST_TIME_BUCKET.getName())).longValue());
        return applicationReferenceAlarm;
    }

    @Override protected XContentBuilder esStreamDataToEsData(ApplicationReferenceAlarm streamData) throws IOException {
        return XContentFactory.jsonBuilder().startObject()
            .field(ApplicationReferenceAlarmTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId())
            .field(ApplicationReferenceAlarmTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId())
            .field(ApplicationReferenceAlarmTable.SOURCE_VALUE.getName(), streamData.getSourceValue())

            .field(ApplicationReferenceAlarmTable.ALARM_TYPE.getName(), streamData.getAlarmType())
            .field(ApplicationReferenceAlarmTable.ALARM_CONTENT.getName(), streamData.getAlarmContent())

            .field(ApplicationReferenceAlarmTable.LAST_TIME_BUCKET.getName(), streamData.getLastTimeBucket())
            .endObject();
    }

    @Override protected String timeBucketColumnNameForDelete() {
        return ApplicationReferenceAlarmTable.LAST_TIME_BUCKET.getName();
    }

    @GraphComputingMetric(name = "/persistence/get/" + ApplicationReferenceAlarmTable.TABLE)
    @Override public final ApplicationReferenceAlarm get(String id) {
        return super.get(id);
    }
}
