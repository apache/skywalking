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

import com.google.protobuf.InvalidProtocolBufferException;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClient;
import org.apache.skywalking.apm.collector.client.shardingjdbc.ShardingjdbcClientException;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentUIDAO;
import org.apache.skywalking.apm.collector.storage.shardingjdbc.base.dao.ShardingjdbcDAO;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentTable;
import org.apache.skywalking.apm.network.proto.TraceSegmentObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author linjiaqi
 */
public class SegmentShardingjdbcUIDAO extends ShardingjdbcDAO implements ISegmentUIDAO {
    private static final Logger logger = LoggerFactory.getLogger(SegmentShardingjdbcUIDAO.class);
    private static final String GET_SEGMENT_SQL = "select {0} from {1} where {2} = ?";

    public SegmentShardingjdbcUIDAO(ShardingjdbcClient client) {
        super(client);
    }

    @Override public TraceSegmentObject load(String segmentId) {
        ShardingjdbcClient client = getClient();
        String sql = SqlBuilder.buildSql(GET_SEGMENT_SQL, SegmentTable.DATA_BINARY.getName(),
            SegmentTable.TABLE, SegmentTable.ID.getName());
        Object[] params = new Object[] {segmentId};
        try (
                ResultSet rs = client.executeQuery(sql, params);
                Statement statement = rs.getStatement();
                Connection conn = statement.getConnection();
            ) {
            if (rs.next()) {
                byte[] dataBinary = rs.getBytes(SegmentTable.DATA_BINARY.getName());
                try {
                    return TraceSegmentObject.parseFrom(dataBinary);
                } catch (InvalidProtocolBufferException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        } catch (SQLException | ShardingjdbcClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }
}
