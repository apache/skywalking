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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceAlarmListTable;

/**
 * @author peng-yongsheng
 */
public class ServiceAlarmListH2PersistenceDAO extends AbstractPersistenceH2DAO<ServiceAlarmList> implements IServiceAlarmListPersistenceDAO<H2SqlEntity, H2SqlEntity, ServiceAlarmList> {

    public ServiceAlarmListH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected String tableName() {
        return ServiceAlarmListTable.TABLE;
    }

    @Override protected ServiceAlarmList h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ServiceAlarmList serviceAlarmList = new ServiceAlarmList();
        serviceAlarmList.setId(resultSet.getString(ServiceAlarmListTable.ID.getName()));
        serviceAlarmList.setSourceValue(resultSet.getInt(ServiceAlarmListTable.SOURCE_VALUE.getName()));

        serviceAlarmList.setAlarmType(resultSet.getInt(ServiceAlarmListTable.ALARM_TYPE.getName()));

        serviceAlarmList.setApplicationId(resultSet.getInt(ServiceAlarmListTable.APPLICATION_ID.getName()));
        serviceAlarmList.setInstanceId(resultSet.getInt(ServiceAlarmListTable.INSTANCE_ID.getName()));
        serviceAlarmList.setServiceId(resultSet.getInt(ServiceAlarmListTable.SERVICE_ID.getName()));

        serviceAlarmList.setTimeBucket(resultSet.getLong(ServiceAlarmListTable.TIME_BUCKET.getName()));
        serviceAlarmList.setAlarmContent(resultSet.getString(ServiceAlarmListTable.ALARM_CONTENT.getName()));

        return serviceAlarmList;
    }

    @Override protected Map<String, Object> streamDataToH2Data(ServiceAlarmList streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ServiceAlarmListTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        target.put(ServiceAlarmListTable.ALARM_TYPE.getName(), streamData.getAlarmType());

        target.put(ServiceAlarmListTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        target.put(ServiceAlarmListTable.INSTANCE_ID.getName(), streamData.getInstanceId());
        target.put(ServiceAlarmListTable.SERVICE_ID.getName(), streamData.getServiceId());

        target.put(ServiceAlarmListTable.TIME_BUCKET.getName(), streamData.getTimeBucket());
        target.put(ServiceAlarmListTable.ALARM_CONTENT.getName(), streamData.getAlarmContent());

        return target;
    }
}
