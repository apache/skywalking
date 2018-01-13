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
        instanceReferenceAlarm.setId(resultSet.getString(ServiceReferenceAlarmTable.COLUMN_ID));
        instanceReferenceAlarm.setSourceValue(resultSet.getInt(ServiceReferenceAlarmTable.COLUMN_SOURCE_VALUE));

        instanceReferenceAlarm.setAlarmType(resultSet.getInt(ServiceReferenceAlarmTable.COLUMN_ALARM_TYPE));

        instanceReferenceAlarm.setFrontApplicationId(resultSet.getInt(ServiceReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID));
        instanceReferenceAlarm.setFrontInstanceId(resultSet.getInt(ServiceReferenceAlarmTable.COLUMN_FRONT_INSTANCE_ID));
        instanceReferenceAlarm.setBehindApplicationId(resultSet.getInt(ServiceReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID));
        instanceReferenceAlarm.setBehindInstanceId(resultSet.getInt(ServiceReferenceAlarmTable.COLUMN_BEHIND_INSTANCE_ID));

        instanceReferenceAlarm.setLastTimeBucket(resultSet.getLong(ServiceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET));
        instanceReferenceAlarm.setAlarmContent(resultSet.getString(ServiceReferenceAlarmTable.COLUMN_ALARM_CONTENT));

        return instanceReferenceAlarm;
    }

    @Override protected Map<String, Object> streamDataToH2Data(InstanceReferenceAlarm streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceAlarmTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(ServiceReferenceAlarmTable.COLUMN_ALARM_TYPE, streamData.getAlarmType());

        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID, streamData.getFrontApplicationId());
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_INSTANCE_ID, streamData.getFrontInstanceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID, streamData.getBehindApplicationId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_INSTANCE_ID, streamData.getBehindInstanceId());

        source.put(ServiceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET, streamData.getLastTimeBucket());
        source.put(ServiceReferenceAlarmTable.COLUMN_ALARM_CONTENT, streamData.getAlarmContent());

        return source;
    }
}
