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

package org.apache.skywalking.apm.collector.storage.shardingjdbc.dao.ui;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationAlarmUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmTable;
import org.apache.skywalking.apm.collector.storage.ui.alarm.Alarm;
import org.apache.skywalking.apm.collector.storage.ui.alarm.AlarmItem;
import org.apache.skywalking.apm.collector.storage.ui.alarm.AlarmType;
import org.apache.skywalking.apm.collector.storage.ui.alarm.CauseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;
import java.util.List;

/**
 * @author linjiaqi
 */
public class ApplicationAlarmShardingjdbcUIDAO extends ShardingjdbcDAO implements IApplicationAlarmUIDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(ApplicationAlarmShardingjdbcUIDAO.class);
    private static final String APPLICATION_ALARM_SQL = "select {0}, {1}, {2}, {3} from {4} where {2} >= ? and {2} <= ? and {1} like ? and {0} in (?) limit ?, ?";

    public ApplicationAlarmShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override
    public Alarm loadAlarmList(String keyword, List<Integer> applicationIds, long startTimeBucket, long endTimeBucket, int limit, int from) throws ParseException {
        ShardingjdbcClient client = getClient();
        
        String tableName = ApplicationAlarmTable.TABLE;
        String sql = SqlBuilder.buildSql(APPLICATION_ALARM_SQL, ApplicationAlarmTable.APPLICATION_ID.getName(), ApplicationAlarmTable.ALARM_CONTENT.getName(), 
                ApplicationAlarmTable.LAST_TIME_BUCKET.getName(), ApplicationAlarmTable.ALARM_TYPE.getName(), tableName);
    
        String applicationIdsParam = applicationIds.toString().replace("[", "").replace("]", "");
        Object[] params = new Object[] {startTimeBucket, endTimeBucket, keyword == null ? "%%" : "%" + keyword + "%", applicationIdsParam, from, limit};
        Alarm alarm = new Alarm();
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            int index = 0;
            while (rs.next()) {
                int applicationId = rs.getInt(ApplicationAlarmTable.APPLICATION_ID.getName());
                String alarmContent = rs.getString(ApplicationAlarmTable.ALARM_CONTENT.getName());
                long lastTimeBucket = rs.getLong(ApplicationAlarmTable.LAST_TIME_BUCKET.getName());
                int alarmType = rs.getInt(ApplicationAlarmTable.ALARM_TYPE.getName());

                AlarmItem alarmItem = new AlarmItem();
                alarmItem.setId(applicationId);
                alarmItem.setContent(alarmContent);
                alarmItem.setStartTime(TimeBucketUtils.INSTANCE.formatMinuteTimeBucket(lastTimeBucket));
                alarmItem.setAlarmType(AlarmType.APPLICATION);
                
                if (org.apache.skywalking.apm.collector.storage.table.alarm.AlarmType.SLOW_RTT.getValue() == alarmType) {
                    alarmItem.setCauseType(CauseType.SLOW_RESPONSE);
                } else if (org.apache.skywalking.apm.collector.storage.table.alarm.AlarmType.ERROR_RATE.getValue() == alarmType) {
                    alarmItem.setCauseType(CauseType.LOW_SUCCESS_RATE);
                }
                index++;
                alarm.getItems().add(alarmItem);
            }
            alarm.setTotal(index);
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        
        return alarm;
    }
}
