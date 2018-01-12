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
        serviceAlarmList.setId(resultSet.getString(ServiceAlarmListTable.COLUMN_ID));
        serviceAlarmList.setSourceValue(resultSet.getInt(ServiceAlarmListTable.COLUMN_SOURCE_VALUE));

        serviceAlarmList.setAlarmType(resultSet.getInt(ServiceAlarmListTable.COLUMN_ALARM_TYPE));

        serviceAlarmList.setApplicationId(resultSet.getInt(ServiceAlarmListTable.COLUMN_APPLICATION_ID));
        serviceAlarmList.setInstanceId(resultSet.getInt(ServiceAlarmListTable.COLUMN_INSTANCE_ID));
        serviceAlarmList.setServiceId(resultSet.getInt(ServiceAlarmListTable.COLUMN_SERVICE_ID));

        serviceAlarmList.setTimeBucket(resultSet.getLong(ServiceAlarmListTable.COLUMN_TIME_BUCKET));
        serviceAlarmList.setAlarmContent(resultSet.getString(ServiceAlarmListTable.COLUMN_ALARM_CONTENT));

        return serviceAlarmList;
    }

    @Override protected Map<String, Object> streamDataToH2Data(ServiceAlarmList streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceAlarmListTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(ServiceAlarmListTable.COLUMN_ALARM_TYPE, streamData.getAlarmType());

        source.put(ServiceAlarmListTable.COLUMN_APPLICATION_ID, streamData.getApplicationId());
        source.put(ServiceAlarmListTable.COLUMN_INSTANCE_ID, streamData.getInstanceId());
        source.put(ServiceAlarmListTable.COLUMN_SERVICE_ID, streamData.getServiceId());

        source.put(ServiceAlarmListTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());
        source.put(ServiceAlarmListTable.COLUMN_ALARM_CONTENT, streamData.getAlarmContent());

        return source;
    }
}
