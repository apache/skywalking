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

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.storage.base.sql.SqlBuilder;
import org.skywalking.apm.collector.storage.dao.INodeMappingPersistenceDAO;
import org.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.skywalking.apm.collector.storage.table.node.NodeMapping;
import org.skywalking.apm.collector.storage.table.node.NodeMappingTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class NodeMappingH2PersistenceDAO extends H2DAO implements INodeMappingPersistenceDAO<H2SqlEntity, H2SqlEntity, NodeMapping> {

    private final Logger logger = LoggerFactory.getLogger(NodeMappingH2PersistenceDAO.class);
    private static final String GET_SQL = "select * from {0} where {1} = ?";

    public NodeMappingH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public NodeMapping get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SQL, NodeMappingTable.TABLE, NodeMappingTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                NodeMapping nodeMapping = new NodeMapping(id);
                nodeMapping.setApplicationId(rs.getInt(NodeMappingTable.COLUMN_APPLICATION_ID));
                nodeMapping.setAddressId(rs.getInt(NodeMappingTable.COLUMN_ADDRESS_ID));
                nodeMapping.setTimeBucket(rs.getLong(NodeMappingTable.COLUMN_TIME_BUCKET));
                return nodeMapping;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(NodeMapping nodeMapping) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(NodeMappingTable.COLUMN_ID, nodeMapping.getId());
        source.put(NodeMappingTable.COLUMN_APPLICATION_ID, nodeMapping.getApplicationId());
        source.put(NodeMappingTable.COLUMN_ADDRESS_ID, nodeMapping.getAddressId());
        source.put(NodeMappingTable.COLUMN_TIME_BUCKET, nodeMapping.getTimeBucket());
        String sql = SqlBuilder.buildBatchInsertSql(NodeMappingTable.TABLE, source.keySet());
        entity.setSql(sql);

        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(NodeMapping nodeMapping) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(NodeMappingTable.COLUMN_APPLICATION_ID, nodeMapping.getApplicationId());
        source.put(NodeMappingTable.COLUMN_ADDRESS_ID, nodeMapping.getAddressId());
        source.put(NodeMappingTable.COLUMN_TIME_BUCKET, nodeMapping.getTimeBucket());
        String sql = SqlBuilder.buildBatchUpdateSql(NodeMappingTable.TABLE, source.keySet(), NodeMappingTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(nodeMapping.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }
}
