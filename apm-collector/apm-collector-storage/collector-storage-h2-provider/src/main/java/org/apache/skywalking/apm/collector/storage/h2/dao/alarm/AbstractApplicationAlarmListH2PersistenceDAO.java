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

package org.apache.skywalking.apm.collector.storage.h2.dao.alarm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmListTable;

/**
 * @author peng-yongsheng
 */
public abstract class AbstractApplicationAlarmListH2PersistenceDAO extends AbstractPersistenceH2DAO<ApplicationAlarmList> {

    public AbstractApplicationAlarmListH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected final ApplicationAlarmList h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ApplicationAlarmList applicationAlarmList = new ApplicationAlarmList();
        applicationAlarmList.setId(resultSet.getString(ApplicationAlarmListTable.COLUMN_ID));
        applicationAlarmList.setMetricId(resultSet.getString(ApplicationAlarmListTable.COLUMN_METRIC_ID));
        applicationAlarmList.setSourceValue(resultSet.getInt(ApplicationAlarmListTable.COLUMN_SOURCE_VALUE));

        applicationAlarmList.setAlarmType(resultSet.getInt(ApplicationAlarmListTable.COLUMN_ALARM_TYPE));

        applicationAlarmList.setApplicationId(resultSet.getInt(ApplicationAlarmListTable.COLUMN_APPLICATION_ID));

        applicationAlarmList.setTimeBucket(resultSet.getLong(ApplicationAlarmListTable.COLUMN_TIME_BUCKET));
        applicationAlarmList.setAlarmContent(resultSet.getString(ApplicationAlarmListTable.COLUMN_ALARM_CONTENT));

        return applicationAlarmList;
    }

    @Override protected final Map<String, Object> streamDataToH2Data(ApplicationAlarmList streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationAlarmListTable.COLUMN_ID, streamData.getId());
        source.put(ApplicationAlarmListTable.COLUMN_METRIC_ID, streamData.getMetricId());
        source.put(ApplicationAlarmListTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(ApplicationAlarmListTable.COLUMN_ALARM_TYPE, streamData.getAlarmType());

        source.put(ApplicationAlarmListTable.COLUMN_APPLICATION_ID, streamData.getApplicationId());

        source.put(ApplicationAlarmListTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());
        source.put(ApplicationAlarmListTable.COLUMN_ALARM_CONTENT, streamData.getAlarmContent());

        return source;
    }
}
