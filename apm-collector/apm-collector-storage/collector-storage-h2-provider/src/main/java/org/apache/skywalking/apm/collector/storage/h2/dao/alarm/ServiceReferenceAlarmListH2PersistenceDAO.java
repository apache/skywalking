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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmListTable;

/**
 * @author peng-yongsheng
 */
public class ServiceReferenceAlarmListH2PersistenceDAO extends AbstractPersistenceH2DAO<ServiceReferenceAlarmList> implements IServiceReferenceAlarmListPersistenceDAO<H2SqlEntity, H2SqlEntity, ServiceReferenceAlarmList> {

    public ServiceReferenceAlarmListH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected String tableName() {
        return ServiceReferenceAlarmListTable.TABLE;
    }

    @Override protected ServiceReferenceAlarmList h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ServiceReferenceAlarmList serviceReferenceAlarmList = new ServiceReferenceAlarmList();
        serviceReferenceAlarmList.setId(resultSet.getString(ServiceReferenceAlarmListTable.ID.getName()));
        serviceReferenceAlarmList.setSourceValue(resultSet.getInt(ServiceReferenceAlarmListTable.SOURCE_VALUE.getName()));

        serviceReferenceAlarmList.setAlarmType(resultSet.getInt(ServiceReferenceAlarmListTable.ALARM_TYPE.getName()));

        serviceReferenceAlarmList.setFrontApplicationId(resultSet.getInt(ServiceReferenceAlarmListTable.FRONT_APPLICATION_ID.getName()));
        serviceReferenceAlarmList.setFrontInstanceId(resultSet.getInt(ServiceReferenceAlarmListTable.FRONT_INSTANCE_ID.getName()));
        serviceReferenceAlarmList.setFrontServiceId(resultSet.getInt(ServiceReferenceAlarmListTable.FRONT_SERVICE_ID.getName()));
        serviceReferenceAlarmList.setBehindApplicationId(resultSet.getInt(ServiceReferenceAlarmListTable.BEHIND_APPLICATION_ID.getName()));
        serviceReferenceAlarmList.setBehindInstanceId(resultSet.getInt(ServiceReferenceAlarmListTable.BEHIND_INSTANCE_ID.getName()));
        serviceReferenceAlarmList.setBehindServiceId(resultSet.getInt(ServiceReferenceAlarmListTable.BEHIND_SERVICE_ID.getName()));

        serviceReferenceAlarmList.setTimeBucket(resultSet.getLong(ServiceReferenceAlarmListTable.TIME_BUCKET.getName()));
        serviceReferenceAlarmList.setAlarmContent(resultSet.getString(ServiceReferenceAlarmListTable.ALARM_CONTENT.getName()));

        return serviceReferenceAlarmList;
    }

    @Override protected Map<String, Object> streamDataToH2Data(ServiceReferenceAlarmList streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ServiceReferenceAlarmListTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        target.put(ServiceReferenceAlarmListTable.ALARM_TYPE.getName(), streamData.getAlarmType());

        target.put(ServiceReferenceAlarmListTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId());
        target.put(ServiceReferenceAlarmListTable.FRONT_INSTANCE_ID.getName(), streamData.getFrontInstanceId());
        target.put(ServiceReferenceAlarmListTable.FRONT_SERVICE_ID.getName(), streamData.getFrontServiceId());
        target.put(ServiceReferenceAlarmListTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId());
        target.put(ServiceReferenceAlarmListTable.BEHIND_INSTANCE_ID.getName(), streamData.getBehindInstanceId());
        target.put(ServiceReferenceAlarmListTable.BEHIND_SERVICE_ID.getName(), streamData.getBehindServiceId());

        target.put(ServiceReferenceAlarmListTable.TIME_BUCKET.getName(), streamData.getTimeBucket());
        target.put(ServiceReferenceAlarmListTable.ALARM_CONTENT.getName(), streamData.getAlarmContent());

        return target;
    }
}
