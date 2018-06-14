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

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.ParseException;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IInstanceAlarmUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.InstanceAlarmTable;
import org.apache.skywalking.apm.collector.storage.ui.alarm.Alarm;
import org.apache.skywalking.apm.collector.storage.ui.alarm.AlarmItem;
import org.apache.skywalking.apm.collector.storage.ui.alarm.AlarmType;
import org.apache.skywalking.apm.collector.storage.ui.alarm.CauseType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class InstanceAlarmShardingjdbcUIDAO extends ShardingjdbcDAO implements IInstanceAlarmUIDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(InstanceAlarmShardingjdbcUIDAO.class);
    private static final String INSTANCE_ALARM_SQL = "select {0}, {1}, {2}, {3} from {4} where {2} >= ? and {2} <= ? and {1} like ? limit ?, ?";
    

    public InstanceAlarmShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public Alarm loadAlarmList(String keyword, long startTimeBucket, long endTimeBucket, int limit, int from) throws ParseException {
        ShardingjdbcClient client = getClient();
        
        String tableName = InstanceAlarmTable.TABLE;
        String sql = SqlBuilder.buildSql(INSTANCE_ALARM_SQL, InstanceAlarmTable.INSTANCE_ID.getName(), InstanceAlarmTable.ALARM_CONTENT.getName(), 
                InstanceAlarmTable.LAST_TIME_BUCKET.getName(), InstanceAlarmTable.ALARM_TYPE.getName(), tableName);
        
        Object[] params = new Object[] {startTimeBucket, endTimeBucket, keyword == null ? "%%" : "%" + keyword + "%", from, limit};
        Alarm alarm = new Alarm();
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            int index = 0;
            while (rs.next()) {
                int instanceId = rs.getInt(InstanceAlarmTable.INSTANCE_ID.getName());
                String alarmContent = rs.getString(InstanceAlarmTable.ALARM_CONTENT.getName());
                long lastTimeBucket = rs.getLong(InstanceAlarmTable.LAST_TIME_BUCKET.getName());
                int alarmType = rs.getInt(InstanceAlarmTable.ALARM_TYPE.getName());

                AlarmItem alarmItem = new AlarmItem();
                alarmItem.setId(instanceId);
                alarmItem.setContent(alarmContent);
                alarmItem.setStartTime(TimeBucketUtils.INSTANCE.formatMinuteTimeBucket(lastTimeBucket));
                alarmItem.setAlarmType(AlarmType.SERVER);
                
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
