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
import org.apache.skywalking.apm.collector.storage.es.base.dao.AbstractPersistenceEsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmListTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractApplicationAlarmListEsPersistenceDAO extends AbstractPersistenceEsDAO<ApplicationAlarmList> {

    AbstractApplicationAlarmListEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override protected final String timeBucketColumnNameForDelete() {
        return ApplicationAlarmListTable.COLUMN_TIME_BUCKET;
    }

    @Override protected final ApplicationAlarmList esDataToStreamData(Map<String, Object> source) {
        ApplicationAlarmList applicationAlarmList = new ApplicationAlarmList();
        applicationAlarmList.setMetricId((String)source.get(ApplicationAlarmListTable.COLUMN_METRIC_ID));
        applicationAlarmList.setApplicationId(((Number)source.get(ApplicationAlarmListTable.COLUMN_APPLICATION_ID)).intValue());
        applicationAlarmList.setSourceValue(((Number)source.get(ApplicationAlarmListTable.COLUMN_SOURCE_VALUE)).intValue());

        applicationAlarmList.setAlarmType(((Number)source.get(ApplicationAlarmListTable.COLUMN_ALARM_TYPE)).intValue());
        applicationAlarmList.setAlarmContent((String)source.get(ApplicationAlarmListTable.COLUMN_ALARM_CONTENT));

        applicationAlarmList.setTimeBucket(((Number)source.get(ApplicationAlarmListTable.COLUMN_TIME_BUCKET)).longValue());
        return applicationAlarmList;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(ApplicationAlarmList streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationAlarmListTable.COLUMN_METRIC_ID, streamData.getMetricId());
        source.put(ApplicationAlarmListTable.COLUMN_APPLICATION_ID, streamData.getApplicationId());
        source.put(ApplicationAlarmListTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(ApplicationAlarmListTable.COLUMN_ALARM_TYPE, streamData.getAlarmType());
        source.put(ApplicationAlarmListTable.COLUMN_ALARM_CONTENT, streamData.getAlarmContent());

        source.put(ApplicationAlarmListTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());
        return source;
    }
}
