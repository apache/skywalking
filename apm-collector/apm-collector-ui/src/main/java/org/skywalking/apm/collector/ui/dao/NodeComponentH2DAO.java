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
import org.skywalking.apm.collector.storage.define.node.NodeComponentTable;
import org.skywalking.apm.collector.storage.h2.SqlBuilder;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.network.trace.component.ComponentsDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class NodeComponentH2DAO extends H2DAO implements INodeComponentDAO {
    private final Logger logger = LoggerFactory.getLogger(NodeComponentH2DAO.class);
    private static final String AGGREGATE_COMPONENT_SQL = "select {0}, {1}, {2} from {3} where {4} >= ? and {4} <= ? group by {0}, {1}, {2} limit 100";

    @Override public JsonArray load(long startTime, long endTime) {
        JsonArray nodeComponentArray = new JsonArray();
        nodeComponentArray.addAll(aggregationComponent(startTime, endTime));
        return nodeComponentArray;
    }

    private JsonArray aggregationComponent(long startTime, long endTime) {
        H2Client client = getClient();

        JsonArray nodeComponentArray = new JsonArray();
        String sql = SqlBuilder.buildSql(AGGREGATE_COMPONENT_SQL, NodeComponentTable.COLUMN_COMPONENT_ID,
            NodeComponentTable.COLUMN_PEER, NodeComponentTable.COLUMN_PEER_ID,
            NodeComponentTable.TABLE, NodeComponentTable.COLUMN_TIME_BUCKET);
        Object[] params = new Object[] {startTime, endTime};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                int peerId = rs.getInt(NodeComponentTable.COLUMN_PEER_ID);
                int componentId = rs.getInt(NodeComponentTable.COLUMN_COMPONENT_ID);
                String componentName = ComponentsDefine.getInstance().getComponentName(componentId);
                if (peerId != 0) {
                    String peer = ApplicationCache.get(peerId);
                    nodeComponentArray.add(buildNodeComponent(peer, componentName));
                }
                String peer = rs.getString(NodeComponentTable.COLUMN_PEER);
                if (StringUtils.isNotEmpty(peer)) {
                    nodeComponentArray.add(buildNodeComponent(peer, componentName));
                }
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return nodeComponentArray;
    }

    private JsonObject buildNodeComponent(String peer, String componentName) {
        JsonObject nodeComponentObj = new JsonObject();
        nodeComponentObj.addProperty("componentName", componentName);
        nodeComponentObj.addProperty("peer", peer);
        return nodeComponentObj;
    }
}
