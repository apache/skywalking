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
import java.util.List;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IResponseTimeDistributionUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.global.ResponseTimeDistributionTable;
import org.apache.skywalking.apm.collector.storage.ui.common.Step;
import org.apache.skywalking.apm.collector.storage.utils.TimePyramidTableNameBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class ResponseTimeDistributionShardingjdbcUIDAO extends ShardingjdbcDAO implements IResponseTimeDistributionUIDAO {
    
    private static final Logger logger = LoggerFactory.getLogger(ResponseTimeDistributionShardingjdbcUIDAO.class);
    private static final String RESPONSE_TIME_DISTRIBUTION_SQL = "select {0}, {1}, {2} from {3} where {4} = ?";

    public ResponseTimeDistributionShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public void loadMetrics(Step step, List<ResponseTimeStep> responseTimeSteps) {
        ShardingjdbcClient client = getClient();
        
        String tableName = TimePyramidTableNameBuilder.build(step, ResponseTimeDistributionTable.TABLE);
        String sql = SqlBuilder.buildSql(RESPONSE_TIME_DISTRIBUTION_SQL, ResponseTimeDistributionTable.CALLS.getName(), ResponseTimeDistributionTable.ERROR_CALLS.getName(), 
                ResponseTimeDistributionTable.SUCCESS_CALLS.getName(), tableName, ResponseTimeDistributionTable.ID.getName());
        
        responseTimeSteps.forEach(responseTimeStep -> {
            String id = responseTimeStep.getDurationPoint() + Const.ID_SPLIT + responseTimeStep.getStep();
            
            try (
                    ResultSet rs = client.executeQuery(sql, new String[] {id});
                    Statement statement = rs.getStatement();
                    Connection conn = statement.getConnection();
                ) {
                if (rs.next()) {
                    responseTimeStep.setCalls(rs.getLong(ResponseTimeDistributionTable.CALLS.getName()));
                    responseTimeStep.setErrorCalls(rs.getLong(ResponseTimeDistributionTable.ERROR_CALLS.getName()));
                    responseTimeStep.setSuccessCalls(rs.getLong(ResponseTimeDistributionTable.SUCCESS_CALLS.getName()));
                }
            } catch (SQLException | ShardingjdbcClientException e) {
                logger.error(e.getMessage(), e);
            }
        });
    }
}
