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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcSqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmTable;

/**
 * @author linjiaqi
 */
public class ServiceReferenceAlarmShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<ServiceReferenceAlarm> implements IServiceReferenceAlarmPersistenceDAO<ShardingjdbcSqlEntity, ShardingjdbcSqlEntity, ServiceReferenceAlarm> {

    public ServiceReferenceAlarmShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return ServiceReferenceAlarmTable.TABLE;
    }

    @Override protected ServiceReferenceAlarm shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        ServiceReferenceAlarm serviceReferenceAlarm = new ServiceReferenceAlarm();
        serviceReferenceAlarm.setId(resultSet.getString(ServiceReferenceAlarmTable.ID.getName()));
        serviceReferenceAlarm.setSourceValue(resultSet.getInt(ServiceReferenceAlarmTable.SOURCE_VALUE.getName()));

        serviceReferenceAlarm.setAlarmType(resultSet.getInt(ServiceReferenceAlarmTable.ALARM_TYPE.getName()));

        serviceReferenceAlarm.setFrontApplicationId(resultSet.getInt(ServiceReferenceAlarmTable.FRONT_APPLICATION_ID.getName()));
        serviceReferenceAlarm.setFrontInstanceId(resultSet.getInt(ServiceReferenceAlarmTable.FRONT_INSTANCE_ID.getName()));
        serviceReferenceAlarm.setFrontServiceId(resultSet.getInt(ServiceReferenceAlarmTable.FRONT_SERVICE_ID.getName()));
        serviceReferenceAlarm.setBehindApplicationId(resultSet.getInt(ServiceReferenceAlarmTable.BEHIND_APPLICATION_ID.getName()));
        serviceReferenceAlarm.setBehindInstanceId(resultSet.getInt(ServiceReferenceAlarmTable.BEHIND_INSTANCE_ID.getName()));
        serviceReferenceAlarm.setBehindServiceId(resultSet.getInt(ServiceReferenceAlarmTable.BEHIND_SERVICE_ID.getName()));

        serviceReferenceAlarm.setLastTimeBucket(resultSet.getLong(ServiceReferenceAlarmTable.LAST_TIME_BUCKET.getName()));
        serviceReferenceAlarm.setAlarmContent(resultSet.getString(ServiceReferenceAlarmTable.ALARM_CONTENT.getName()));

        return serviceReferenceAlarm;
    }

    @Override protected Map<String, Object> streamDataToShardingjdbcData(ServiceReferenceAlarm streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ServiceReferenceAlarmTable.ID.getName(), streamData.getId());
        target.put(ServiceReferenceAlarmTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        target.put(ServiceReferenceAlarmTable.ALARM_TYPE.getName(), streamData.getAlarmType());

        target.put(ServiceReferenceAlarmTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId());
        target.put(ServiceReferenceAlarmTable.FRONT_INSTANCE_ID.getName(), streamData.getFrontInstanceId());
        target.put(ServiceReferenceAlarmTable.FRONT_SERVICE_ID.getName(), streamData.getFrontServiceId());
        target.put(ServiceReferenceAlarmTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId());
        target.put(ServiceReferenceAlarmTable.BEHIND_INSTANCE_ID.getName(), streamData.getBehindInstanceId());
        target.put(ServiceReferenceAlarmTable.BEHIND_SERVICE_ID.getName(), streamData.getBehindServiceId());

        target.put(ServiceReferenceAlarmTable.LAST_TIME_BUCKET.getName(), streamData.getLastTimeBucket());
        target.put(ServiceReferenceAlarmTable.ALARM_CONTENT.getName(), streamData.getAlarmContent());

        return target;
    }
    
    @Override protected String timeBucketColumnNameForDelete() {
        return ServiceReferenceAlarmTable.LAST_TIME_BUCKET.getName();
    }
    
    @GraphComputingMetric(name = "/persistence/get/" + ServiceReferenceAlarmTable.TABLE)
    @Override public ServiceReferenceAlarm get(String id) {
        return super.get(id);
    }
}
