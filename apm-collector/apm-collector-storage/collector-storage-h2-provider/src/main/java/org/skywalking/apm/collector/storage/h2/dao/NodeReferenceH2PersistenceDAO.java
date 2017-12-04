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
import org.skywalking.apm.collector.storage.dao.INodeReferencePersistenceDAO;
import org.skywalking.apm.collector.storage.h2.base.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.base.define.H2SqlEntity;
import org.skywalking.apm.collector.storage.table.noderef.NodeReference;
import org.skywalking.apm.collector.storage.table.noderef.NodeReferenceTable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng, clevertension
 */
public class NodeReferenceH2PersistenceDAO extends H2DAO implements INodeReferencePersistenceDAO<H2SqlEntity, H2SqlEntity, NodeReference> {

    private final Logger logger = LoggerFactory.getLogger(NodeReferenceH2PersistenceDAO.class);
    private static final String GET_SQL = "select * from {0} where {1} = ?";

    public NodeReferenceH2PersistenceDAO(H2Client client) {
        super(client);
    }

    @Override public NodeReference get(String id) {
        H2Client client = getClient();
        String sql = SqlBuilder.buildSql(GET_SQL, NodeReferenceTable.TABLE, NodeReferenceTable.COLUMN_ID);
        Object[] params = new Object[] {id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                NodeReference nodeReference = new NodeReference(id);
                nodeReference.setFrontApplicationId(rs.getInt(NodeReferenceTable.COLUMN_FRONT_APPLICATION_ID));
                nodeReference.setBehindApplicationId(rs.getInt(NodeReferenceTable.COLUMN_BEHIND_APPLICATION_ID));
                nodeReference.setS1Lte(rs.getInt(NodeReferenceTable.COLUMN_S1_LTE));
                nodeReference.setS3Lte(rs.getInt(NodeReferenceTable.COLUMN_S3_LTE));
                nodeReference.setS5Lte(rs.getInt(NodeReferenceTable.COLUMN_S5_LTE));
                nodeReference.setS5Gt(rs.getInt(NodeReferenceTable.COLUMN_S5_GT));
                nodeReference.setSummary(rs.getInt(NodeReferenceTable.COLUMN_SUMMARY));
                nodeReference.setError(rs.getInt(NodeReferenceTable.COLUMN_ERROR));
                nodeReference.setTimeBucket(rs.getLong(NodeReferenceTable.COLUMN_TIME_BUCKET));
                return nodeReference;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(NodeReference data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(NodeReferenceTable.COLUMN_ID, data.getId());
        source.put(NodeReferenceTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(NodeReferenceTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(NodeReferenceTable.COLUMN_S1_LTE, data.getS1Lte());
        source.put(NodeReferenceTable.COLUMN_S3_LTE, data.getS3Lte());
        source.put(NodeReferenceTable.COLUMN_S5_LTE, data.getS5Lte());
        source.put(NodeReferenceTable.COLUMN_S5_GT, data.getS5Gt());
        source.put(NodeReferenceTable.COLUMN_SUMMARY, data.getSummary());
        source.put(NodeReferenceTable.COLUMN_ERROR, data.getError());
        source.put(NodeReferenceTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        String sql = SqlBuilder.buildBatchInsertSql(NodeReferenceTable.TABLE, source.keySet());
        entity.setSql(sql);

        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(NodeReference data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(NodeReferenceTable.COLUMN_FRONT_APPLICATION_ID, data.getFrontApplicationId());
        source.put(NodeReferenceTable.COLUMN_BEHIND_APPLICATION_ID, data.getBehindApplicationId());
        source.put(NodeReferenceTable.COLUMN_S1_LTE, data.getS1Lte());
        source.put(NodeReferenceTable.COLUMN_S3_LTE, data.getS3Lte());
        source.put(NodeReferenceTable.COLUMN_S5_LTE, data.getS5Lte());
        source.put(NodeReferenceTable.COLUMN_S5_GT, data.getS5Gt());
        source.put(NodeReferenceTable.COLUMN_SUMMARY, data.getSummary());
        source.put(NodeReferenceTable.COLUMN_ERROR, data.getError());
        source.put(NodeReferenceTable.COLUMN_TIME_BUCKET, data.getTimeBucket());
        String sql = SqlBuilder.buildBatchUpdateSql(NodeReferenceTable.TABLE, source.keySet(), NodeReferenceTable.COLUMN_ID);
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(data.getId());
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }

    @Override public void deleteHistory(Long startTimestamp, Long endTimestamp) {
    }
}
