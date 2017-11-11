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
import org.skywalking.apm.collector.storage.dao.INodeComponentPersistenceDAO;
import org.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.skywalking.apm.collector.storage.table.node.NodeComponent;
import org.skywalking.apm.collector.storage.table.node.NodeComponentTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class NodeComponentH2PersistenceDAO extends H2DAO implements INodeComponentPersistenceDAO<H2SqlEntity, H2SqlEntity, NodeComponent> {
    private final Logger logger = LoggerFactory.getLogger(NodeComponentH2PersistenceDAO.class);
    private static final String GET_SQL = "select * from {0} where {1} = ?";

    @Override
    public NodeComponent get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SQL, NodeComponentTable.TABLE, NodeComponentTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                NodeComponent nodeComponent = new NodeComponent(id);
                nodeComponent.setComponentId(rs.getInt(NodeComponentTable.COLUMN_COMPONENT_ID));
                nodeComponent.setComponentName(rs.getString(NodeComponentTable.COLUMN_COMPONENT_NAME));
                nodeComponent.setPeerId(rs.getInt(NodeComponentTable.COLUMN_PEER_ID));
                nodeComponent.setPeer(rs.getString(NodeComponentTable.COLUMN_PEER));
                nodeComponent.setTimeBucket(rs.getLong(NodeComponentTable.COLUMN_TIME_BUCKET));
                return nodeComponent;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public H2SqlEntity prepareBatchInsert(NodeComponent data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(NodeComponentTable.COLUMN_ID, data.getId());
        source.put(NodeComponentTable.COLUMN_COMPONENT_ID, data.getComponentId());
        source.put(NodeComponentTable.COLUMN_COMPONENT_NAME, data.getComponentName());
        source.put(NodeComponentTable.COLUMN_PEER_ID, data.getPeerId());
        source.put(NodeComponentTable.COLUMN_PEER, data.getPeer());
        source.put(NodeComponentTable.COLUMN_TIME_BUCKET, data.getTimeBucket());

        String sql = SqlBuilder.buildBatchInsertSql(NodeComponentTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override
    public H2SqlEntity prepareBatchUpdate(NodeComponent data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(NodeComponentTable.COLUMN_COMPONENT_ID, data.getComponentId());
        source.put(NodeComponentTable.COLUMN_COMPONENT_NAME, data.getComponentName());
        source.put(NodeComponentTable.COLUMN_PEER_ID, data.getPeerId());
        source.put(NodeComponentTable.COLUMN_PEER, data.getPeer());
        source.put(NodeComponentTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        String sql = SqlBuilder.buildBatchUpdateSql(NodeComponentTable.TABLE, source.keySet(), NodeComponentTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(data.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }
}
