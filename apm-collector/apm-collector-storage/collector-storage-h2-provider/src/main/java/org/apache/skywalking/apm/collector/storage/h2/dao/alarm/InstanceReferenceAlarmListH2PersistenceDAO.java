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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceReferenceAlarmListTable;

/**
 * @author peng-yongsheng
 */
public class InstanceReferenceAlarmListH2PersistenceDAO extends AbstractPersistenceH2DAO<InstanceReferenceAlarmList> implements IInstanceReferenceAlarmListPersistenceDAO<H2SqlEntity, H2SqlEntity, InstanceReferenceAlarmList> {

    public InstanceReferenceAlarmListH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected String tableName() {
        return InstanceReferenceAlarmListTable.TABLE;
    }

    @Override protected InstanceReferenceAlarmList h2DataToStreamData(ResultSet resultSet) throws SQLException {
        InstanceReferenceAlarmList instanceReferenceAlarmList = new InstanceReferenceAlarmList();
        instanceReferenceAlarmList.setId(resultSet.getString(InstanceReferenceAlarmListTable.COLUMN_ID));
        instanceReferenceAlarmList.setSourceValue(resultSet.getInt(InstanceReferenceAlarmListTable.COLUMN_SOURCE_VALUE));

        instanceReferenceAlarmList.setAlarmType(resultSet.getInt(InstanceReferenceAlarmListTable.COLUMN_ALARM_TYPE));

        instanceReferenceAlarmList.setFrontApplicationId(resultSet.getInt(InstanceReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID));
        instanceReferenceAlarmList.setFrontInstanceId(resultSet.getInt(InstanceReferenceAlarmListTable.COLUMN_FRONT_INSTANCE_ID));
        instanceReferenceAlarmList.setBehindApplicationId(resultSet.getInt(InstanceReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID));
        instanceReferenceAlarmList.setBehindInstanceId(resultSet.getInt(InstanceReferenceAlarmListTable.COLUMN_BEHIND_INSTANCE_ID));

        instanceReferenceAlarmList.setTimeBucket(resultSet.getLong(InstanceReferenceAlarmListTable.COLUMN_TIME_BUCKET));
        instanceReferenceAlarmList.setAlarmContent(resultSet.getString(InstanceReferenceAlarmListTable.COLUMN_ALARM_CONTENT));

        return instanceReferenceAlarmList;
    }

    @Override protected Map<String, Object> streamDataToH2Data(InstanceReferenceAlarmList streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceReferenceAlarmListTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(InstanceReferenceAlarmListTable.COLUMN_ALARM_TYPE, streamData.getAlarmType());

        source.put(InstanceReferenceAlarmListTable.COLUMN_FRONT_APPLICATION_ID, streamData.getFrontApplicationId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_FRONT_INSTANCE_ID, streamData.getFrontInstanceId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_BEHIND_APPLICATION_ID, streamData.getBehindApplicationId());
        source.put(InstanceReferenceAlarmListTable.COLUMN_BEHIND_INSTANCE_ID, streamData.getBehindInstanceId());

        source.put(InstanceReferenceAlarmListTable.COLUMN_TIME_BUCKET, streamData.getTimeBucket());
        source.put(InstanceReferenceAlarmListTable.COLUMN_ALARM_CONTENT, streamData.getAlarmContent());

        return source;
    }
}
