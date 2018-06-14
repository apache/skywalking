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
import java.util.ArrayList;
import java.util.List;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.IGlobalTraceUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.global.GlobalTraceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class GlobalTraceShardingjdbcUIDAO extends ShardingjdbcDAO implements IGlobalTraceUIDAO {

    private static final Logger logger = LoggerFactory.getLogger(GlobalTraceShardingjdbcUIDAO.class);

    private static final String GET_GLOBAL_TRACE_ID_SQL = "select {0} from {1} where {2} = ? limit 10";
    private static final String GET_SEGMENT_IDS_SQL = "select {0} from {1} where {2} = ? limit 10";

    public GlobalTraceShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public List<String> getGlobalTraceId(String segmentId) {
        List<String> globalTraceIds = new ArrayList<>();
        ShardingjdbcClient client = getClient();
        String sql = SqlBuilder.buildSql(GET_GLOBAL_TRACE_ID_SQL, GlobalTraceTable.TRACE_ID.getName(),
            GlobalTraceTable.TABLE, GlobalTraceTable.SEGMENT_ID.getName());
        Object[] params = new Object[] {segmentId};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                String globalTraceId = rs.getString(GlobalTraceTable.TRACE_ID.getName());
                logger.debug("segmentId: {}, global trace id: {}", segmentId, globalTraceId);
                globalTraceIds.add(globalTraceId);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return globalTraceIds;
    }

    @Override public List<String> getSegmentIds(String globalTraceId) {
        List<String> segmentIds = new ArrayList<>();
        ShardingjdbcClient client = getClient();
        String sql = SqlBuilder.buildSql(GET_SEGMENT_IDS_SQL, GlobalTraceTable.SEGMENT_ID.getName(),
            GlobalTraceTable.TABLE, GlobalTraceTable.TRACE_ID);
        Object[] params = new Object[] {globalTraceId};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            while (rs.next()) {
                String segmentId = rs.getString(GlobalTraceTable.SEGMENT_ID.getName());
                logger.debug("segmentId: {}, global trace id: {}", segmentId, globalTraceId);
                segmentIds.add(segmentId);
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return segmentIds;
    }
}
