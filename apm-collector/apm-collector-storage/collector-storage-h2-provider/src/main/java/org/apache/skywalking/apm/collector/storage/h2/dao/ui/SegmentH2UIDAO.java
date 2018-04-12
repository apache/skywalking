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


package org.apache.skywalking.apm.collector.storage.h2.dao.ui;

import com.google.protobuf.InvalidProtocolBufferException;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.dao.ui.ISegmentUIDAO;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.segment.SegmentTable;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.network.proto.TraceSegmentObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class SegmentH2UIDAO extends H2DAO implements ISegmentUIDAO {
    private final Logger logger = LoggerFactory.getLogger(SegmentH2UIDAO.class);
    private static final String GET_SEGMENT_SQL = "select {0} from {1} where {2} = ?";

    public SegmentH2UIDAO(H2Client client) {
        super(client);
    }

    @Override public TraceSegmentObject load(String segmentId) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SEGMENT_SQL, SegmentTable.DATA_BINARY.getName(),
            SegmentTable.TABLE, SegmentTable.ID.getName());
        Object[] params = new Object[] {segmentId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                byte[] dataBinary = rs.getBytes(SegmentTable.DATA_BINARY.getName());
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
