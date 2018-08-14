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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.*;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.common.xcontent.*;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceAlarmListEsPersistenceDAO extends AbstractPersistenceEsDAO<ApplicationReferenceAlarmList> implements IApplicationReferenceAlarmListPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ApplicationReferenceAlarmList> {

    public ApplicationReferenceAlarmListEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return ApplicationReferenceAlarmListTable.TABLE;
    }

    @Override protected ApplicationReferenceAlarmList esDataToStreamData(Map<String, Object> source) {
        ApplicationReferenceAlarmList applicationReferenceAlarmList = new ApplicationReferenceAlarmList();
        applicationReferenceAlarmList.setFrontApplicationId(((Number)source.get(ApplicationReferenceAlarmListTable.FRONT_APPLICATION_ID.getName())).intValue());
        applicationReferenceAlarmList.setBehindApplicationId(((Number)source.get(ApplicationReferenceAlarmListTable.BEHIND_APPLICATION_ID.getName())).intValue());
        applicationReferenceAlarmList.setSourceValue(((Number)source.get(ApplicationReferenceAlarmListTable.SOURCE_VALUE.getName())).intValue());

        applicationReferenceAlarmList.setAlarmType(((Number)source.get(ApplicationReferenceAlarmListTable.ALARM_TYPE.getName())).intValue());
        applicationReferenceAlarmList.setAlarmContent((String)source.get(ApplicationReferenceAlarmListTable.ALARM_CONTENT.getName()));

        applicationReferenceAlarmList.setTimeBucket(((Number)source.get(ApplicationReferenceAlarmListTable.TIME_BUCKET.getName())).longValue());
        return applicationReferenceAlarmList;
    }

    @Override
    protected XContentBuilder esStreamDataToEsData(ApplicationReferenceAlarmList streamData) throws IOException {
        return XContentFactory.jsonBuilder().startObject()
            .field(ApplicationReferenceAlarmListTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId())
            .field(ApplicationReferenceAlarmListTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId())
            .field(ApplicationReferenceAlarmListTable.SOURCE_VALUE.getName(), streamData.getSourceValue())

            .field(ApplicationReferenceAlarmListTable.ALARM_TYPE.getName(), streamData.getAlarmType())
            .field(ApplicationReferenceAlarmListTable.ALARM_CONTENT.getName(), streamData.getAlarmContent())

            .field(ApplicationReferenceAlarmListTable.TIME_BUCKET.getName(), streamData.getTimeBucket())
            .endObject();
    }

    @Override protected String timeBucketColumnNameForDelete() {
        return ApplicationReferenceAlarmListTable.TIME_BUCKET.getName();
    }

    @GraphComputingMetric(name = "/persistence/get/" + ApplicationReferenceAlarmListTable.TABLE)
    @Override public ApplicationReferenceAlarmList get(String id) {
        return super.get(id);
    }
}
