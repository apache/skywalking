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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IInstanceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmListTable;

/**
 * @author peng-yongsheng
 */
public class InstanceAlarmListH2PersistenceDAO extends AbstractPersistenceH2DAO<InstanceAlarmList> implements IInstanceAlarmListPersistenceDAO<H2SqlEntity, H2SqlEntity, InstanceAlarmList> {

    public InstanceAlarmListH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected String tableName() {
        return InstanceAlarmListTable.TABLE;
    }

    @Override protected InstanceAlarmList h2DataToStreamData(ResultSet resultSet) throws SQLException {
        InstanceAlarmList instanceAlarmList = new InstanceAlarmList();
        instanceAlarmList.setId(resultSet.getString(InstanceAlarmListTable.ID.getName()));
        instanceAlarmList.setSourceValue(resultSet.getInt(InstanceAlarmListTable.SOURCE_VALUE.getName()));

        instanceAlarmList.setAlarmType(resultSet.getInt(InstanceAlarmListTable.ALARM_TYPE.getName()));

        instanceAlarmList.setApplicationId(resultSet.getInt(InstanceAlarmListTable.APPLICATION_ID.getName()));
        instanceAlarmList.setInstanceId(resultSet.getInt(InstanceAlarmListTable.INSTANCE_ID.getName()));

        instanceAlarmList.setTimeBucket(resultSet.getLong(InstanceAlarmListTable.TIME_BUCKET.getName()));
        instanceAlarmList.setAlarmContent(resultSet.getString(InstanceAlarmListTable.ALARM_CONTENT.getName()));

        return instanceAlarmList;
    }

    @Override protected Map<String, Object> streamDataToH2Data(InstanceAlarmList streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(InstanceAlarmListTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        target.put(InstanceAlarmListTable.ALARM_TYPE.getName(), streamData.getAlarmType());

        target.put(InstanceAlarmListTable.APPLICATION_ID.getName(), streamData.getApplicationId());
        target.put(InstanceAlarmListTable.INSTANCE_ID.getName(), streamData.getInstanceId());

        target.put(InstanceAlarmListTable.TIME_BUCKET.getName(), streamData.getTimeBucket());
        target.put(InstanceAlarmListTable.ALARM_CONTENT.getName(), streamData.getAlarmContent());

        return target;
    }
}
