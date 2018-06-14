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
import java.util.LinkedList;
import java.util.List;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IApplicationAlarmListUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.alarm.ApplicationAlarmListTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class ApplicationAlarmListShardingjdbcUIDAO extends ShardingjdbcDAO implements IApplicationAlarmListUIDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(ApplicationAlarmListShardingjdbcUIDAO.class);
    private static final String APPLICATION_ALARM_LIST_SQL = "select {0}, {1}, count({1}) as cnt from {2} where {0} >= ? and {0} <= ? group by {0}, {1} limit 100";

    public ApplicationAlarmListShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public List<AlarmTrend> getAlarmedApplicationNum(Step step, long startTimeBucket, long endTimeBucket) {
        ShardingjdbcClient client = getClient();
        
        String tableName = TimePyramidTableNameBuilder.build(step, ApplicationAlarmListTable.TABLE);
        
        List<AlarmTrend> alarmTrends = new LinkedList<>();
        String sql = SqlBuilder.buildSql(APPLICATION_ALARM_LIST_SQL, ApplicationAlarmListTable.TIME_BUCKET.getName(), 
                ApplicationAlarmListTable.APPLICATION_ID.getName(), tableName);
        
        Object[] params = new Object[] {startTimeBucket, endTimeBucket};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                long timeBucket = rs.getLong(ApplicationAlarmListTable.TIME_BUCKET.getName());
                int cnt = rs.getInt("cnt");

                AlarmTrend alarmTrend = new AlarmTrend();
                alarmTrend.setTimeBucket(timeBucket);
                alarmTrend.setNumberOfApplication(cnt);
                alarmTrends.add(alarmTrend);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        
        return alarmTrends;
    }
}
