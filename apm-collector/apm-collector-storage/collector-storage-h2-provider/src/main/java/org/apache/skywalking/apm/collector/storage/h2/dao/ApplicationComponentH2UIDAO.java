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


package org.apache.skywalking.apm.collector.storage.h2.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.skywalking.apm.collector.client.h2.H2Client;
import org.apache.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.apache.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.apache.skywalking.apm.collector.storage.table.application.ApplicationComponentTable;
import org.apache.skywalking.apm.collector.client.h2.H2ClientException;
import org.apache.skywalking.apm.collector.storage.dao.IApplicationComponentUIDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class ApplicationComponentH2UIDAO extends H2DAO implements IApplicationComponentUIDAO {

    private final Logger logger = LoggerFactory.getLogger(ApplicationComponentH2UIDAO.class);
    private static final String AGGREGATE_COMPONENT_SQL = "select {0}, {1} from {2} where {3} >= ? and {3} <= ? group by {0}, {1} limit 100";

    public ApplicationComponentH2UIDAO(H2Client client) {
        super(client);
    }

    @Override public JsonArray load(long startTime, long endTime) {
        JsonArray applicationComponentArray = new JsonArray();
        applicationComponentArray.addAll(aggregationComponent(startTime, endTime));
        return applicationComponentArray;
    }

    private JsonArray aggregationComponent(long startTime, long endTime) {
        H2Client client = getClient();

        JsonArray applicationComponentArray = new JsonArray();
        String sql = SqlBuilder.buildSql(AGGREGATE_COMPONENT_SQL, ApplicationComponentTable.COLUMN_COMPONENT_ID, ApplicationComponentTable.COLUMN_PEER_ID,
            ApplicationComponentTable.TABLE, ApplicationComponentTable.COLUMN_TIME_BUCKET);
        Object[] params = new Object[] {startTime, endTime};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                int peerId = rs.getInt(ApplicationComponentTable.COLUMN_PEER_ID);
                int componentId = rs.getInt(ApplicationComponentTable.COLUMN_COMPONENT_ID);
                JsonObject applicationComponentObj = new JsonObject();
                applicationComponentObj.addProperty(ApplicationComponentTable.COLUMN_COMPONENT_ID, componentId);
                applicationComponentObj.addProperty(ApplicationComponentTable.COLUMN_PEER_ID, peerId);
                applicationComponentArray.add(applicationComponentObj);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return applicationComponentArray;
    }
}
