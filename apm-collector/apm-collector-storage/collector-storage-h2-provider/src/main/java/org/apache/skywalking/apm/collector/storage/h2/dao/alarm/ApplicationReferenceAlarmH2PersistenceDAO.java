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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationReferenceAlarmPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarm;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarmTable;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceAlarmH2PersistenceDAO extends AbstractPersistenceH2DAO<ApplicationReferenceAlarm> implements IApplicationReferenceAlarmPersistenceDAO<H2SqlEntity, H2SqlEntity, ApplicationReferenceAlarm> {

    public ApplicationReferenceAlarmH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected String tableName() {
        return ApplicationReferenceAlarmTable.TABLE;
    }

    @Override protected ApplicationReferenceAlarm h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ApplicationReferenceAlarm applicationReferenceAlarm = new ApplicationReferenceAlarm();
        applicationReferenceAlarm.setId(resultSet.getString(ApplicationReferenceAlarmTable.ID.getName()));
        applicationReferenceAlarm.setSourceValue(resultSet.getInt(ApplicationReferenceAlarmTable.SOURCE_VALUE.getName()));

        applicationReferenceAlarm.setAlarmType(resultSet.getInt(ApplicationReferenceAlarmTable.ALARM_TYPE.getName()));

        applicationReferenceAlarm.setFrontApplicationId(resultSet.getInt(ApplicationReferenceAlarmTable.FRONT_APPLICATION_ID.getName()));
        applicationReferenceAlarm.setBehindApplicationId(resultSet.getInt(ApplicationReferenceAlarmTable.BEHIND_APPLICATION_ID.getName()));

        applicationReferenceAlarm.setLastTimeBucket(resultSet.getLong(ApplicationReferenceAlarmTable.LAST_TIME_BUCKET.getName()));
        applicationReferenceAlarm.setAlarmContent(resultSet.getString(ApplicationReferenceAlarmTable.ALARM_CONTENT.getName()));

        return applicationReferenceAlarm;
    }

    @Override protected Map<String, Object> streamDataToH2Data(ApplicationReferenceAlarm streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ApplicationReferenceAlarmTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        target.put(ApplicationReferenceAlarmTable.ALARM_TYPE.getName(), streamData.getAlarmType());

        target.put(ApplicationReferenceAlarmTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId());
        target.put(ApplicationReferenceAlarmTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId());

        target.put(ApplicationReferenceAlarmTable.LAST_TIME_BUCKET.getName(), streamData.getLastTimeBucket());
        target.put(ApplicationReferenceAlarmTable.ALARM_CONTENT.getName(), streamData.getAlarmContent());

        return target;
    }
}
