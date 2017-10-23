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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.skywalking.apm.collector.cache.ApplicationCache;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.define.node.NodeMappingTable;
import org.skywalking.apm.collector.storage.h2.SqlBuilder;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class NodeMappingH2DAO extends H2DAO implements INodeMappingDAO {
    private final Logger logger = LoggerFactory.getLogger(NodeMappingH2DAO.class);
    private static final String NODE_MAPPING_SQL = "select {0}, {1}, {2} from {3} where {4} >= ? and {4} <= ? group by {0}, {1}, {2} limit 100";

    @Override public JsonArray load(long startTime, long endTime) {
        H2Client client = getClient();
        JsonArray nodeMappingArray = new JsonArray();
        String sql = SqlBuilder.buildSql(NODE_MAPPING_SQL, NodeMappingTable.COLUMN_APPLICATION_ID,
            NodeMappingTable.COLUMN_ADDRESS_ID, NodeMappingTable.COLUMN_ADDRESS,
            NodeMappingTable.TABLE, NodeMappingTable.COLUMN_TIME_BUCKET);

        Object[] params = new Object[] {startTime, endTime};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                int applicationId = rs.getInt(NodeMappingTable.COLUMN_APPLICATION_ID);
                String applicationCode = ApplicationCache.get(applicationId);
                int addressId = rs.getInt(NodeMappingTable.COLUMN_ADDRESS_ID);
                if (addressId != 0) {
                    String address = ApplicationCache.get(addressId);
                    JsonObject nodeMappingObj = new JsonObject();
                    nodeMappingObj.addProperty("applicationCode", applicationCode);
                    nodeMappingObj.addProperty("address", address);
                    nodeMappingArray.add(nodeMappingObj);
                }
                String address = rs.getString(NodeMappingTable.COLUMN_ADDRESS);
                if (StringUtils.isNotEmpty(address)) {
                    JsonObject nodeMappingObj = new JsonObject();
                    nodeMappingObj.addProperty("applicationCode", applicationCode);
                    nodeMappingObj.addProperty("address", address);
                    nodeMappingArray.add(nodeMappingObj);
                }
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        logger.debug("node mapping data: {}", nodeMappingArray.toString());
        return nodeMappingArray;
    }
}
