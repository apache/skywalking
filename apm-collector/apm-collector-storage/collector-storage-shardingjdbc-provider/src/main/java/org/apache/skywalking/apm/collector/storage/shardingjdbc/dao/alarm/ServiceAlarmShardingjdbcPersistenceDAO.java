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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcSqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarmTable;

/**
 * @author linjiaqi
 */
public class ServiceAlarmShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<ServiceAlarm> implements IServiceAlarmPersistenceDAO<ShardingjdbcSqlEntity, ShardingjdbcSqlEntity, ServiceAlarm> {

    public ServiceAlarmShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return ServiceAlarmTable.TABLE;
    }

    @Override protected ServiceAlarm shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        ServiceAlarm serviceAlarm = new ServiceAlarm();
        serviceAlarm.setId(resultSet.getString(ServiceAlarmTable.ID.getName()));
        serviceAlarm.setSourceValue(resultSet.getInt(ServiceAlarmTable.SOURCE_VALUE.getName()));

        serviceAlarm.setAlarmType(resultSet.getInt(ServiceAlarmTable.ALARM_TYPE.getName()));

        serviceAlarm.setApplicationId(resultSet.getInt(ServiceAlarmTable.APPLICATION_ID.getName()));
        serviceAlarm.setInstanceId(resultSet.getInt(ServiceAlarmTable.INSTANCE_ID.getName()));
        serviceAlarm.setServiceId(resultSet.getInt(ServiceAlarmTable.SERVICE_ID.getName()));

        serviceAlarm.setLastTimeBucket(resultSet.getLong(ServiceAlarmTable.LAST_TIME_BUCKET.getName()));
        serviceAlarm.setAlarmContent(resultSet.getString(ServiceAlarmTable.ALARM_CONTENT.getName()));

        return serviceAlarm;
    }

    @Override protected Map<String, Object> streamDataToShardingjdbcData(ServiceAlarm streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ServiceAlarmTable.ID.getName(), streamData.getId());
        target.put(ServiceAlarmTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        target.put(ServiceAlarmTable.ALARM_TYPE.getName(), streamData.getAlarmType());

        target.put(ServiceAlarmTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        target.put(ServiceAlarmTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(ServiceAlarmTable.SERVICE_ID.getName(), streamData.getServiceId());

        target.put(ServiceAlarmTable.LAST_TIME_BUCKET.getName(), streamData.getLastTimeBucket());
        target.put(ServiceAlarmTable.ALARM_CONTENT.getName(), streamData.getAlarmContent());

        return target;
    }
    
    @Override protected String timeBucketColumnNameForDelete() {
        return ServiceAlarmTable.LAST_TIME_BUCKET.getName();
    }
    
    @GraphComputingMetric(name = "/persistence/get/" + ServiceAlarmTable.TABLE)
    @Override public ServiceAlarm get(String id) {
        return super.get(id);
    }
}
