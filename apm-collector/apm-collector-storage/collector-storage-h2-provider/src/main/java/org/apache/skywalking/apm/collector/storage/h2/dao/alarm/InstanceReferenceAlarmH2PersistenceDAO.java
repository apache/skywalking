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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarmTable;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmTable;

/**
 * @author peng-yongsheng
 */
public class InstanceReferenceAlarmH2PersistenceDAO extends AbstractPersistenceH2DAO<InstanceReferenceAlarm> implements IInstanceReferenceAlarmPersistenceDAO<H2SqlEntity, H2SqlEntity, InstanceReferenceAlarm> {

    public InstanceReferenceAlarmH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected String tableName() {
        return InstanceReferenceAlarmTable.TABLE;
    }

    @Override protected InstanceReferenceAlarm h2DataToStreamData(ResultSet resultSet) throws SQLException {
        InstanceReferenceAlarm instanceReferenceAlarm = new InstanceReferenceAlarm();
        instanceReferenceAlarm.setId(resultSet.getString(ServiceReferenceAlarmTable.ID.getName()));
        instanceReferenceAlarm.setSourceValue(resultSet.getInt(ServiceReferenceAlarmTable.SOURCE_VALUE.getName()));

        instanceReferenceAlarm.setAlarmType(resultSet.getInt(ServiceReferenceAlarmTable.ALARM_TYPE.getName()));

        instanceReferenceAlarm.setFrontApplicationId(resultSet.getInt(ServiceReferenceAlarmTable.FRONT_APPLICATION_ID.getName()));
        instanceReferenceAlarm.setFrontInstanceId(resultSet.getInt(ServiceReferenceAlarmTable.FRONT_INSTANCE_ID.getName()));
        instanceReferenceAlarm.setBehindApplicationId(resultSet.getInt(ServiceReferenceAlarmTable.BEHIND_APPLICATION_ID.getName()));
        instanceReferenceAlarm.setBehindInstanceId(resultSet.getInt(ServiceReferenceAlarmTable.BEHIND_INSTANCE_ID.getName()));

        instanceReferenceAlarm.setLastTimeBucket(resultSet.getLong(ServiceReferenceAlarmTable.LAST_TIME_BUCKET.getName()));
        instanceReferenceAlarm.setAlarmContent(resultSet.getString(ServiceReferenceAlarmTable.ALARM_CONTENT.getName()));

        return instanceReferenceAlarm;
    }

    @Override protected Map<String, Object> streamDataToH2Data(InstanceReferenceAlarm streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ServiceReferenceAlarmTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        target.put(ServiceReferenceAlarmTable.ALARM_TYPE.getName(), streamData.getAlarmType());

        target.put(ServiceReferenceAlarmTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId());
        target.put(ServiceReferenceAlarmTable.FRONT_INSTANCE_ID.getName(), streamData.getFrontInstanceId());
        target.put(ServiceReferenceAlarmTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId());
        target.put(ServiceReferenceAlarmTable.BEHIND_INSTANCE_ID.getName(), streamData.getBehindInstanceId());

        target.put(ServiceReferenceAlarmTable.LAST_TIME_BUCKET.getName(), streamData.getLastTimeBucket());
        target.put(ServiceReferenceAlarmTable.ALARM_CONTENT.getName(), streamData.getAlarmContent());

        return target;
    }
}
