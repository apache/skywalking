/*
 * Copyright 2017, OpenSkywalking Organization All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Project repository: https://github.com/OpenSkywalking/skywalking
 */

package org.skywalking.apm.collector.ui.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.storage.define.global.GlobalTraceTable;
import org.skywalking.apm.collector.storage.h2.SqlBuilder;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class GlobalTraceH2DAO extends H2DAO implements IGlobalTraceDAO {
    private final Logger logger = LoggerFactory.getLogger(GlobalTraceH2DAO.class);
    private static final String GET_GLOBAL_TRACE_ID_SQL = "select {0} from {1} where {2} = ? limit 10";
    private static final String GET_SEGMENT_IDS_SQL = "select {0} from {1} where {2} = ? limit 10";
    @Override public List<String> getGlobalTraceId(String segmentId) {
        List<String> globalTraceIds = new ArrayList<>();
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_GLOBAL_TRACE_ID_SQL, GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID,
                GlobalTraceTable.TABLE, GlobalTraceTable.COLUMN_SEGMENT_ID);
        Object[] params = new Object[]{segmentId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                String globalTraceId = rs.getString(GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID);
                logger.debug("segmentId: {}, global trace id: {}", segmentId, globalTraceId);
                globalTraceIds.add(globalTraceId);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return globalTraceIds;
    }

    @Override public List<String> getSegmentIds(String globalTraceId) {
        List<String> segmentIds = new ArrayList<>();
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SEGMENT_IDS_SQL, GlobalTraceTable.COLUMN_SEGMENT_ID,
                GlobalTraceTable.TABLE, GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID);
        Object[] params = new Object[]{globalTraceId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                String segmentId = rs.getString(GlobalTraceTable.COLUMN_SEGMENT_ID);
                logger.debug("segmentId: {}, global trace id: {}", segmentId, globalTraceId);
                segmentIds.add(segmentId);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return segmentIds;
    }
}
