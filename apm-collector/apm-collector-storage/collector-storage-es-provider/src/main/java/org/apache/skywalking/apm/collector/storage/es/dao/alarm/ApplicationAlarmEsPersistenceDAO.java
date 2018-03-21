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
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.es.base.dao.EsDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmTable;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.index.reindex.BulkByScrollResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class ApplicationAlarmEsPersistenceDAO extends EsDAO implements IApplicationAlarmPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder, ApplicationAlarm> {

    private final Logger logger = LoggerFactory.getLogger(ApplicationAlarmEsPersistenceDAO.class);

    public ApplicationAlarmEsPersistenceDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public ApplicationAlarm get(String id) {
        GetResponse getResponse = getClient().prepareGet(ApplicationAlarmTable.TABLE, id).get();
        if (getResponse.isExists()) {
            ApplicationAlarm instanceAlarm = new ApplicationAlarm();
            instanceAlarm.setId(id);

            Map<String, Object> source = getResponse.getSource();
            instanceAlarm.setApplicationId(((Number) source.get(ApplicationAlarmTable.COLUMN_APPLICATION_ID)).intValue());
            instanceAlarm.setSourceValue(((Number) source.get(ApplicationAlarmTable.COLUMN_SOURCE_VALUE)).intValue());

            instanceAlarm.setAlarmType(((Number) source.get(ApplicationAlarmTable.COLUMN_ALARM_TYPE)).intValue());
            instanceAlarm.setAlarmContent((String) source.get(ApplicationAlarmTable.COLUMN_ALARM_CONTENT));

            instanceAlarm.setLastTimeBucket(((Number) source.get(ApplicationAlarmTable.COLUMN_LAST_TIME_BUCKET)).longValue());
            return instanceAlarm;
        } else {
            return null;
        }
    }

    @Override
    public IndexRequestBuilder prepareBatchInsert(ApplicationAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationAlarmTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ApplicationAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ApplicationAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ApplicationAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ApplicationAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return getClient().prepareIndex(ApplicationAlarmTable.TABLE, data.getId()).setSource(source);
    }

    @Override
    public UpdateRequestBuilder prepareBatchUpdate(ApplicationAlarm data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationAlarmTable.COLUMN_APPLICATION_ID, data.getApplicationId());
        source.put(ApplicationAlarmTable.COLUMN_SOURCE_VALUE, data.getSourceValue());

        source.put(ApplicationAlarmTable.COLUMN_ALARM_TYPE, data.getAlarmType());
        source.put(ApplicationAlarmTable.COLUMN_ALARM_CONTENT, data.getAlarmContent());

        source.put(ApplicationAlarmTable.COLUMN_LAST_TIME_BUCKET, data.getLastTimeBucket());

        return getClient().prepareUpdate(ApplicationAlarmTable.TABLE, data.getId()).setDoc(source);
    }

    @Override
    public void deleteHistory(Long startTimestamp, Long endTimestamp) {
        long startTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(startTimestamp);
        long endTimeBucket = TimeBucketUtils.INSTANCE.getMinuteTimeBucket(endTimestamp);
        BulkByScrollResponse response = getClient().prepareDelete(
                QueryBuilders.rangeQuery(ApplicationAlarmTable.COLUMN_LAST_TIME_BUCKET).gte(startTimeBucket).lte(endTimeBucket)
                , ApplicationAlarmTable.TABLE)
                .get();

        long deleted = response.getDeleted();
        logger.info("Delete {} rows history from {} index.", deleted, ApplicationAlarmTable.TABLE);
    }
}
