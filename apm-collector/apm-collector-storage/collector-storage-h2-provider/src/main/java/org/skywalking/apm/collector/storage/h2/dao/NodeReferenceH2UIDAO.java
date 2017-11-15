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

package org.skywalking.apm.collector.storage.h2.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.skywalking.apm.collector.storage.dao.INodeReferenceUIDAO;
import org.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.skywalking.apm.collector.storage.table.noderef.NodeReferenceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class NodeReferenceH2UIDAO extends H2DAO implements INodeReferenceUIDAO {

    private final Logger logger = LoggerFactory.getLogger(NodeReferenceH2UIDAO.class);
    private static final String NODE_REFERENCE_SQL = "select {8}, {9}, sum({0}) as {0}, sum({1}) as {1}, sum({2}) as {2}, " +
        "sum({3}) as {3}, sum({4}) as {4}, sum({5}) as {5} from {6} where {7} >= ? and {7} <= ? group by {8}, {9} limit 100";

    public NodeReferenceH2UIDAO(H2Client client) {
        super(client);
    }

    @Override public JsonArray load(long startTime, long endTime) {
        H2Client client = getClient();
        JsonArray nodeRefResSumArray = new JsonArray();
        String sql = SqlBuilder.buildSql(NODE_REFERENCE_SQL, NodeReferenceTable.COLUMN_S1_LTE,
            NodeReferenceTable.COLUMN_S3_LTE, NodeReferenceTable.COLUMN_S5_LTE,
            NodeReferenceTable.COLUMN_S5_GT, NodeReferenceTable.COLUMN_SUMMARY,
            NodeReferenceTable.COLUMN_ERROR, NodeReferenceTable.TABLE, NodeReferenceTable.COLUMN_TIME_BUCKET,
            NodeReferenceTable.COLUMN_FRONT_APPLICATION_ID, NodeReferenceTable.COLUMN_BEHIND_APPLICATION_ID);

        Object[] params = new Object[] {startTime, endTime};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                int frontApplicationId = rs.getInt(NodeReferenceTable.COLUMN_FRONT_APPLICATION_ID);
                int behindApplicationId = rs.getInt(NodeReferenceTable.COLUMN_BEHIND_APPLICATION_ID);
                JsonObject nodeRefResSumObj = new JsonObject();
                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(NodeReferenceTable.COLUMN_FRONT_APPLICATION_ID), frontApplicationId);
                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(NodeReferenceTable.COLUMN_BEHIND_APPLICATION_ID), behindApplicationId);
                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(NodeReferenceTable.COLUMN_S1_LTE), rs.getDouble(NodeReferenceTable.COLUMN_S1_LTE));
                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(NodeReferenceTable.COLUMN_S3_LTE), rs.getDouble(NodeReferenceTable.COLUMN_S3_LTE));
                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(NodeReferenceTable.COLUMN_S5_LTE), rs.getDouble(NodeReferenceTable.COLUMN_S5_LTE));
                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(NodeReferenceTable.COLUMN_S5_GT), rs.getDouble(NodeReferenceTable.COLUMN_S5_GT));
                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(NodeReferenceTable.COLUMN_ERROR), rs.getDouble(NodeReferenceTable.COLUMN_ERROR));
                nodeRefResSumObj.addProperty(ColumnNameUtils.INSTANCE.rename(NodeReferenceTable.COLUMN_SUMMARY), rs.getDouble(NodeReferenceTable.COLUMN_SUMMARY));
                nodeRefResSumArray.add(nodeRefResSumObj);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return nodeRefResSumArray;
    }
}
