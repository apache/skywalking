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

import com.google.protobuf.InvalidProtocolBufferException;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.storage.define.segment.SegmentTable;
import org.skywalking.apm.collector.storage.h2.SqlBuilder;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class SegmentH2DAO extends H2DAO implements ISegmentDAO {
    private final Logger logger = LoggerFactory.getLogger(SegmentH2DAO.class);
    private static final String GET_SEGMENT_SQL = "select {0} from {1} where {2} = ?";

    @Override public TraceSegmentObject load(String segmentId) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SEGMENT_SQL, SegmentTable.COLUMN_DATA_BINARY,
            SegmentTable.TABLE, SegmentTable.COLUMN_ID);
        Object[] params = new Object[] {segmentId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                byte[] dataBinary = rs.getBytes(SegmentTable.COLUMN_DATA_BINARY);
                try {
                    return TraceSegmentObject.parseFrom(dataBinary);
                } catch (InvalidProtocolBufferException e) {
                    logger.error(e.getMessage(), e);
                }
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }
}
