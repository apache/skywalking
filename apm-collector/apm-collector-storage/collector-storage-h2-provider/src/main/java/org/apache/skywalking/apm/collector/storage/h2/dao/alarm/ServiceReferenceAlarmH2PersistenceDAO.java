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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmTable;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceAlarmH2PersistenceDAO extends AbstractPersistenceH2DAO<ServiceReferenceAlarm> implements IServiceReferenceAlarmPersistenceDAO<H2SqlEntity, H2SqlEntity, ServiceReferenceAlarm> {

    public ServiceReferenceAlarmH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected String tableName() {
        return ServiceReferenceAlarmTable.TABLE;
    }

    @Override protected ServiceReferenceAlarm h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ServiceReferenceAlarm serviceReferenceAlarm = new ServiceReferenceAlarm();
        serviceReferenceAlarm.setId(resultSet.getString(ServiceReferenceAlarmTable.COLUMN_ID));
        serviceReferenceAlarm.setSourceValue(resultSet.getInt(ServiceReferenceAlarmTable.COLUMN_SOURCE_VALUE));

        serviceReferenceAlarm.setAlarmType(resultSet.getInt(ServiceReferenceAlarmTable.COLUMN_ALARM_TYPE));

        serviceReferenceAlarm.setFrontApplicationId(resultSet.getInt(ServiceReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID));
        serviceReferenceAlarm.setFrontInstanceId(resultSet.getInt(ServiceReferenceAlarmTable.COLUMN_FRONT_INSTANCE_ID));
        serviceReferenceAlarm.setFrontServiceId(resultSet.getInt(ServiceReferenceAlarmTable.COLUMN_FRONT_SERVICE_ID));
        serviceReferenceAlarm.setBehindApplicationId(resultSet.getInt(ServiceReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID));
        serviceReferenceAlarm.setBehindInstanceId(resultSet.getInt(ServiceReferenceAlarmTable.COLUMN_BEHIND_INSTANCE_ID));
        serviceReferenceAlarm.setBehindServiceId(resultSet.getInt(ServiceReferenceAlarmTable.COLUMN_BEHIND_SERVICE_ID));

        serviceReferenceAlarm.setLastTimeBucket(resultSet.getLong(ServiceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET));
        serviceReferenceAlarm.setAlarmContent(resultSet.getString(ServiceReferenceAlarmTable.COLUMN_ALARM_CONTENT));

        return serviceReferenceAlarm;
    }

    @Override protected Map<String, Object> streamDataToH2Data(ServiceReferenceAlarm streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceAlarmTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(ServiceReferenceAlarmTable.COLUMN_ALARM_TYPE, streamData.getAlarmType());

        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID, streamData.getFrontApplicationId());
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_INSTANCE_ID, streamData.getFrontInstanceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_FRONT_SERVICE_ID, streamData.getFrontServiceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID, streamData.getBehindApplicationId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_INSTANCE_ID, streamData.getBehindInstanceId());
        source.put(ServiceReferenceAlarmTable.COLUMN_BEHIND_SERVICE_ID, streamData.getBehindServiceId());

        source.put(ServiceReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET, streamData.getLastTimeBucket());
        source.put(ServiceReferenceAlarmTable.COLUMN_ALARM_CONTENT, streamData.getAlarmContent());

        return source;
    }
}
