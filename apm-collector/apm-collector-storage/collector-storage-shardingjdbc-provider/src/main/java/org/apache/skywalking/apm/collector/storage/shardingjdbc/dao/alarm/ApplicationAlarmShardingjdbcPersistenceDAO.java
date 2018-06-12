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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.alarm;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.core.annotations.trace.GraphComputingMetric;
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcSqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmTable;

/**
 * @author linjiaqi
 */
public class ApplicationAlarmShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<ApplicationAlarm> implements IApplicationAlarmPersistenceDAO<ShardingjdbcSqlEntity, ShardingjdbcSqlEntity, ApplicationAlarm> {

    public ApplicationAlarmShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }
    
    @Override protected String tableName() {
        return ApplicationAlarmTable.TABLE;
    }

    @Override protected ApplicationAlarm shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        ApplicationAlarm applicationAlarm = new ApplicationAlarm();
        applicationAlarm.setId(resultSet.getString(ApplicationAlarmTable.ID.getName()));
        applicationAlarm.setSourceValue(resultSet.getInt(ApplicationAlarmTable.SOURCE_VALUE.getName()));

        applicationAlarm.setAlarmType(resultSet.getInt(ApplicationAlarmTable.ALARM_TYPE.getName()));

        applicationAlarm.setApplicationId(resultSet.getInt(ApplicationAlarmTable.APPLICATION_ID.getName()));

        applicationAlarm.setLastTimeBucket(resultSet.getLong(ApplicationAlarmTable.LAST_TIME_BUCKET.getName()));
        applicationAlarm.setAlarmContent(resultSet.getString(ApplicationAlarmTable.ALARM_CONTENT.getName()));

        return applicationAlarm;
    }

    @Override protected Map<String, Object> streamDataToShardingjdbcData(ApplicationAlarm streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ApplicationAlarmTable.ID.getName(), streamData.getId());
        target.put(ApplicationAlarmTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        target.put(ApplicationAlarmTable.ALARM_TYPE.getName(), streamData.getAlarmType());

        target.put(ApplicationAlarmTable.APPLICATION_ID.getName(), streamData.getApplicationId());

        target.put(ApplicationAlarmTable.LAST_TIME_BUCKET.getName(), streamData.getLastTimeBucket());
        target.put(ApplicationAlarmTable.ALARM_CONTENT.getName(), streamData.getAlarmContent());

        return target;
    }

    @Override protected String timeBucketColumnNameForDelete() {
        return ApplicationAlarmTable.LAST_TIME_BUCKET.getName();
    }
    
    @GraphComputingMetric(name = "/persistence/get/" + ApplicationAlarmTable.TABLE)
    @Override public ApplicationAlarm get(String id) {
        return super.get(id);
    }
}
