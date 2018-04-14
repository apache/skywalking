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
        return ApplicationAlarmListTable.TIME_BUCKET.getName();
    }

    @Override protected final ApplicationAlarmList esDataToStreamData(Map<String, Object> source) {
        ApplicationAlarmList applicationAlarmList = new ApplicationAlarmList();
        applicationAlarmList.setMetricId((String)source.get(ApplicationAlarmListTable.METRIC_ID.getName()));
        applicationAlarmList.setApplicationId(((Number)source.get(ApplicationAlarmListTable.APPLICATION_ID.getName())).intValue());
        applicationAlarmList.setSourceValue(((Number)source.get(ApplicationAlarmListTable.SOURCE_VALUE.getName())).intValue());

        applicationAlarmList.setAlarmType(((Number)source.get(ApplicationAlarmListTable.ALARM_TYPE.getName())).intValue());
        applicationAlarmList.setAlarmContent((String)source.get(ApplicationAlarmListTable.ALARM_CONTENT.getName()));

        applicationAlarmList.setTimeBucket(((Number)source.get(ApplicationAlarmListTable.TIME_BUCKET.getName())).longValue());
        return applicationAlarmList;
    }

    @Override protected final Map<String, Object> esStreamDataToEsData(ApplicationAlarmList streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ApplicationAlarmListTable.METRIC_ID.getName(), streamData.getMetricId());
        target.put(ApplicationAlarmListTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        target.put(ApplicationAlarmListTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        target.put(ApplicationAlarmListTable.ALARM_TYPE.getName(), streamData.getAlarmType());
        target.put(ApplicationAlarmListTable.ALARM_CONTENT.getName(), streamData.getAlarmContent());

        target.put(ApplicationAlarmListTable.TIME_BUCKET.getName(), streamData.getTimeBucket());
        return target;
    }

    @GraphComputingMetric(name = "/persistence/get/" + ApplicationAlarmListTable.TABLE)
    @Override public final ApplicationAlarmList get(String id) {
        return super.get(id);
    }
}
