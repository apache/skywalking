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
        applicationReferenceAlarm.setId(resultSet.getString(ApplicationReferenceAlarmTable.COLUMN_ID));
        applicationReferenceAlarm.setSourceValue(resultSet.getInt(ApplicationReferenceAlarmTable.COLUMN_SOURCE_VALUE));

        applicationReferenceAlarm.setAlarmType(resultSet.getInt(ApplicationReferenceAlarmTable.COLUMN_ALARM_TYPE));

        applicationReferenceAlarm.setFrontApplicationId(resultSet.getInt(ApplicationReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID));
        applicationReferenceAlarm.setBehindApplicationId(resultSet.getInt(ApplicationReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID));

        applicationReferenceAlarm.setLastTimeBucket(resultSet.getLong(ApplicationReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET));
        applicationReferenceAlarm.setAlarmContent(resultSet.getString(ApplicationReferenceAlarmTable.COLUMN_ALARM_CONTENT));

        return applicationReferenceAlarm;
    }

    @Override protected Map<String, Object> streamDataToH2Data(ApplicationReferenceAlarm streamData) {
        Map<String, Object> source = new HashMap<>();
        source.put(ApplicationReferenceAlarmTable.COLUMN_SOURCE_VALUE, streamData.getSourceValue());

        source.put(ApplicationReferenceAlarmTable.COLUMN_ALARM_TYPE, streamData.getAlarmType());

        source.put(ApplicationReferenceAlarmTable.COLUMN_FRONT_APPLICATION_ID, streamData.getFrontApplicationId());
        source.put(ApplicationReferenceAlarmTable.COLUMN_BEHIND_APPLICATION_ID, streamData.getBehindApplicationId());

        source.put(ApplicationReferenceAlarmTable.COLUMN_LAST_TIME_BUCKET, streamData.getLastTimeBucket());
        source.put(ApplicationReferenceAlarmTable.COLUMN_ALARM_CONTENT, streamData.getAlarmContent());

        return source;
    }
}
