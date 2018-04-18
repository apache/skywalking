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
import org.apache.skywalking.apm.collector.storage.dao.alarm.IApplicationReferenceAlarmListPersistenceDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.AbstractPersistenceH2DAO;
import org.apache.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarmList;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationReferenceAlarmListTable;

/**
 * @author peng-yongsheng
 */
public class ApplicationReferenceAlarmListH2PersistenceDAO extends AbstractPersistenceH2DAO<ApplicationReferenceAlarmList> implements IApplicationReferenceAlarmListPersistenceDAO<H2SqlEntity, H2SqlEntity, ApplicationReferenceAlarmList> {

    public ApplicationReferenceAlarmListH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override protected String tableName() {
        return ApplicationReferenceAlarmListTable.TABLE;
    }

    @Override protected ApplicationReferenceAlarmList h2DataToStreamData(ResultSet resultSet) throws SQLException {
        ApplicationReferenceAlarmList applicationReferenceAlarmList = new ApplicationReferenceAlarmList();
        applicationReferenceAlarmList.setId(resultSet.getString(ApplicationReferenceAlarmListTable.ID.getName()));
        applicationReferenceAlarmList.setSourceValue(resultSet.getInt(ApplicationReferenceAlarmListTable.SOURCE_VALUE.getName()));

        applicationReferenceAlarmList.setAlarmType(resultSet.getInt(ApplicationReferenceAlarmListTable.ALARM_TYPE.getName()));

        applicationReferenceAlarmList.setFrontApplicationId(resultSet.getInt(ApplicationReferenceAlarmListTable.FRONT_APPLICATION_ID.getName()));
        applicationReferenceAlarmList.setBehindApplicationId(resultSet.getInt(ApplicationReferenceAlarmListTable.BEHIND_APPLICATION_ID.getName()));

        applicationReferenceAlarmList.setTimeBucket(resultSet.getLong(ApplicationReferenceAlarmListTable.TIME_BUCKET.getName()));
        applicationReferenceAlarmList.setAlarmContent(resultSet.getString(ApplicationReferenceAlarmListTable.ALARM_CONTENT.getName()));

        return applicationReferenceAlarmList;
    }

    @Override protected Map<String, Object> streamDataToH2Data(ApplicationReferenceAlarmList streamData) {
        Map<String, Object> target = new HashMap<>();
        target.put(ApplicationReferenceAlarmListTable.SOURCE_VALUE.getName(), streamData.getSourceValue());

        target.put(ApplicationReferenceAlarmListTable.ALARM_TYPE.getName(), streamData.getAlarmType());

        target.put(ApplicationReferenceAlarmListTable.FRONT_APPLICATION_ID.getName(), streamData.getFrontApplicationId());
        target.put(ApplicationReferenceAlarmListTable.BEHIND_APPLICATION_ID.getName(), streamData.getBehindApplicationId());

        target.put(ApplicationReferenceAlarmListTable.TIME_BUCKET.getName(), streamData.getTimeBucket());
        target.put(ApplicationReferenceAlarmListTable.ALARM_CONTENT.getName(), streamData.getAlarmContent());

        return target;
    }
}
