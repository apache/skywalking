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
        serviceReferenceAlarmList.setId(resultSet.getString(ServiceReferenceAlarmListTable.COLUMN_ID));
        serviceReferenceAlarmList.setSourceValue(resultSet.getInt(ServiceReferenceAlarmListTable.COLUMN_SOURCE_VALUE));

        serviceReferenceAlarmList.setAlarmType(resultSet.getInt(ServiceReferenceAlarmListTable.COLUMN_ALARM_TYPE));

        serviceReferenceAlarmList.setFrontApplicationId(resultSet.getInt(ServiceReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID));
        serviceReferenceAlarmList.setFrontInstanceId(resultSet.getInt(ServiceReferenceAlarmListTable.COLUMN_FRONT_INSTANCE_ID));
        serviceReferenceAlarmList.setFrontServiceId(resultSet.getInt(ServiceReferenceAlarmListTable.COLUMN_FRONT_SERVICE_ID));
        serviceReferenceAlarmList.setBehindApplicationId(resultSet.getInt(ServiceReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID));
        serviceReferenceAlarmList.setBehindInstanceId(resultSet.getInt(ServiceReferenceAlarmListTable.COLUMN_BEHIND_INSTANCE_ID));
        serviceReferenceAlarmList.setBehindServiceId(resultSet.getInt(ServiceReferenceAlarmListTable.COLUMN_BEHIND_SERVICE_ID));

        serviceReferenceAlarmList.setTimeBucket(resultSet.getLong(ServiceReferenceAlarmListTable.COLUMN_TIME_BUCKET));
        serviceReferenceAlarmList.setAlarmContent(resultSet.getString(ServiceReferenceAlarmListTable.COLUMN_ALARM_CONTENT));

        return serviceReferenceAlarmList;
    }

    @Override protected Map<String, Object> streamDataToH2Data(ServiceReferenceAlarmList streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceAlarmListTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(ServiceReferenceAlarmListTable.COLUMN_ALARM_TYPE, streamData.getAlarmType());

        source.put(ServiceReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID, streamData.getFrontApplicationId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_FRONT_INSTANCE_ID, streamData.getFrontInstanceId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_FRONT_SERVICE_ID, streamData.getFrontServiceId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID, streamData.getBehindApplicationId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_BEHIND_INSTANCE_ID, streamData.getBehindInstanceId());
        source.put(ServiceReferenceAlarmListTable.COLUMN_BEHIND_SERVICE_ID, streamData.getBehindServiceId());

        source.put(ServiceReferenceAlarmListTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());
        source.put(ServiceReferenceAlarmListTable.COLUMN_ALARM_CONTENT, streamData.getAlarmContent());

        return source;
    }
}
