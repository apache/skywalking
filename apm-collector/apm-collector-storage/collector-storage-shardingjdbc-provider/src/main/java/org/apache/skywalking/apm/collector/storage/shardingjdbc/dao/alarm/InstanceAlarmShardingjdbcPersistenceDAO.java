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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcSqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmTable;

/**
 * @author linjiaqi
 */
public class InstanceAlarmShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<InstanceAlarm> implements IInstanceAlarmPersistenceDAO<ShardingjdbcSqlEntity, ShardingjdbcSqlEntity, InstanceAlarm> {

    public InstanceAlarmShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return InstanceAlarmTable.TABLE;
    }

    @Override protected InstanceAlarm shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        InstanceAlarm instanceAlarm = new InstanceAlarm();
        instanceAlarm.setId(resultSet.getString(InstanceAlarmTable.ID.getName()));
        instanceAlarm.setSourceValue(resultSet.getInt(InstanceAlarmTable.SOURCE_VALUE.getName()));

        instanceAlarm.setAlarmType(resultSet.getInt(InstanceAlarmTable.ALARM_TYPE.getName()));

        instanceAlarm.setApplicationId(resultSet.getInt(InstanceAlarmTable.APPLICATION_ID.getName()));
        instanceAlarm.setInstanceId(resultSet.getInt(InstanceAlarmTable.INSTANCE_ID.getName()));

        instanceAlarm.setLastTimeBucket(resultSet.getLong(InstanceAlarmTable.LAST_TIME_BUCKET.getName()));
        instanceAlarm.setAlarmContent(resultSet.getString(InstanceAlarmTable.ALARM_CONTENT.getName()));

        return instanceAlarm;
    }

    @Override protected Map<String, Object> streamDataToShardingjdbcData(InstanceAlarm streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(InstanceAlarmTable.ID.getName(), streamData.getId());
        target.put(InstanceAlarmTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        target.put(InstanceAlarmTable.ALARM_TYPE.getName(), streamData.getAlarmType());

        target.put(InstanceAlarmTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        target.put(InstanceAlarmTable.INSTANCE_ID.getName(), streamData.getInstanceId());

        target.put(InstanceAlarmTable.LAST_TIME_BUCKET.getName(), streamData.getLastTimeBucket());
        target.put(InstanceAlarmTable.ALARM_CONTENT.getName(), streamData.getAlarmContent());

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
