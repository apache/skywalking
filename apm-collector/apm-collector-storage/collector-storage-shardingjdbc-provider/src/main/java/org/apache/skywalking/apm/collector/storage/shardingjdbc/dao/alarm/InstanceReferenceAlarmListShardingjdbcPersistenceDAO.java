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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.AbstractPersistenceShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.define.ShardingjdbcSqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarmListTable;

/**
 * @author linjiaqi
 */
public class InstanceReferenceAlarmListShardingjdbcPersistenceDAO extends AbstractPersistenceShardingjdbcDAO<InstanceReferenceAlarmList> implements IInstanceReferenceAlarmListPersistenceDAO<ShardingjdbcSqlEntity, ShardingjdbcSqlEntity, InstanceReferenceAlarmList> {

    public InstanceReferenceAlarmListShardingjdbcPersistenceDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override protected String tableName() {
        return InstanceReferenceAlarmListTable.TABLE;
    }

    @Override protected InstanceReferenceAlarmList shardingjdbcDataToStreamData(ResultSet resultSet) throws SQLException {
        InstanceReferenceAlarmList instanceReferenceAlarmList = new InstanceReferenceAlarmList();
        instanceReferenceAlarmList.setId(resultSet.getString(InstanceReferenceAlarmListTable.ID.getName()));
        instanceReferenceAlarmList.setSourceValue(resultSet.getInt(InstanceReferenceAlarmListTable.SOURCE_VALUE.getName()));

        instanceReferenceAlarmList.setAlarmType(resultSet.getInt(InstanceReferenceAlarmListTable.ALARM_TYPE.getName()));

        instanceReferenceAlarmList.setFrontApplicationId(resultSet.getInt(InstanceReferenceAlarmListTable.FRONT_APPLICATION_ID.getName()));
        instanceReferenceAlarmList.setFrontInstanceId(resultSet.getInt(InstanceReferenceAlarmListTable.FRONT_INSTANCE_ID.getName()));
        instanceReferenceAlarmList.setBehindApplicationId(resultSet.getInt(InstanceReferenceAlarmListTable.BEHIND_APPLICATION_ID.getName()));
        instanceReferenceAlarmList.setBehindInstanceId(resultSet.getInt(InstanceReferenceAlarmListTable.BEHIND_INSTANCE_ID.getName()));

        instanceReferenceAlarmList.setTimeBucket(resultSet.getLong(InstanceReferenceAlarmListTable.TIME_BUCKET.getName()));
        instanceReferenceAlarmList.setAlarmContent(resultSet.getString(InstanceReferenceAlarmListTable.ALARM_CONTENT.getName()));

        return instanceReferenceAlarmList;
    }

    @Override protected Map<String, Object> streamDataToShardingjdbcData(InstanceReferenceAlarmList streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(InstanceReferenceAlarmListTable.ID.getName(), streamData.getId());
        target.put(InstanceReferenceAlarmListTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        target.put(InstanceReferenceAlarmListTable.ALARM_TYPE.getName(), streamData.getAlarmType());

        target.put(InstanceReferenceAlarmListTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId());
        target.put(InstanceReferenceAlarmListTable.FRONT_INSTANCE_ID.getName(), streamData.getFrontInstanceId());
        target.put(InstanceReferenceAlarmListTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId());
        target.put(InstanceReferenceAlarmListTable.BEHIND_INSTANCE_ID.getName(), streamData.getBehindInstanceId());

        target.put(InstanceReferenceAlarmListTable.TIME_BUCKET.getName(), streamData.getTimeBucket());
        target.put(InstanceReferenceAlarmListTable.ALARM_CONTENT.getName(), streamData.getAlarmContent());

        return target;
    }
    
    @Override protected String timeBucketColumnNameForDelete() {
        return InstanceReferenceAlarmListTable.TIME_BUCKET.getName();
    }
    
    @GraphComputingMetric(name = "/persistence/get/" + InstanceReferenceAlarmListTable.TABLE)
    @Override public InstanceReferenceAlarmList get(String id) {
        return super.get(id);
    }
}
