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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IServiceReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcSqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.ServiceReferenceAlarmListTable;

/**
 * @author linjiaqi
 */
public class ServiceReferenceAlarmListShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<ServiceReferenceAlarmList> implements IServiceReferenceAlarmListPersistenceDAO<ShardingjdbcSqlEntity, ShardingjdbcSqlEntity, ServiceReferenceAlarmList> {

    public ServiceReferenceAlarmListShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return ServiceReferenceAlarmListTable.TABLE;
    }

    @Override protected ServiceReferenceAlarmList shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
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

    @Override protected Map<String, Object> streamDataToShardingjdbcData(ServiceReferenceAlarmList streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ServiceReferenceAlarmListTable.ID.getName(), streamData.getId());
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
    
    @Override protected String timeBucketColumnNameForDelete() {
        return ServiceReferenceAlarmListTable.TIME_BUCKET.getName();
    }
    
    @GraphComputingMetric(name = "/persistence/get/" + ServiceReferenceAlarmListTable.TABLE)
    @Override public ServiceReferenceAlarmList get(String id) {
        return super.get(id);
    }
}
