package org.skywalking.apm.collector.agentstream.worker.node.mapping.dao;

import org.skywalking.apm.collector.agentstream.worker.node.component.dao.NodeComponentH2DAO;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.node.NodeMappingTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.define.H2SqlEntity;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.*;

/**
 * @author pengys5, clevertension
 */
public class NodeMappingH2DAO extends H2DAO implements INodeMappingDAO, IPersistenceDAO<H2SqlEntity, H2SqlEntity> {
    private final Logger logger = LoggerFactory.getLogger(NodeComponentH2DAO.class);
    private static final String GET_SQL = "select * from {0} where {1} = ?";
    @Override public Data get(String id, DataDefine dataDefine) {
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_SQL, NodeMappingTable.TABLE, "id");
        Object[] params = new Object[]{id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                Data data = dataDefine.build(id);
                data.setDataInteger(0, rs.getInt(NodeMappingTable.COLUMN_APPLICATION_ID));
                data.setDataInteger(1, rs.getInt(NodeMappingTable.COLUMN_ADDRESS_ID));
                data.setDataString(1, rs.getString(NodeMappingTable.COLUMN_ADDRESS));
                data.setDataLong(0, rs.getLong(NodeMappingTable.COLUMN_TIME_BUCKET));
                return data;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }
    @Override public H2SqlEntity prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put("id", data.getDataString(0));
        source.put(NodeMappingTable.COLUMN_APPLICATION_ID, data.getDataInteger(0));
        source.put(NodeMappingTable.COLUMN_ADDRESS_ID, data.getDataInteger(1));
        source.put(NodeMappingTable.COLUMN_ADDRESS, data.getDataString(1));
        source.put(NodeMappingTable.COLUMN_TIME_BUCKET, data.getDataLong(0));
        String sql = getBatchInsertSql(NodeMappingTable.TABLE, source.keySet());
        entity.setSql(sql);

        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }
    @Override public H2SqlEntity prepareBatchUpdate(Data data) {
        Map<String, Object> source = new HashMap<>();
        H2SqlEntity entity = new H2SqlEntity();
        source.put(NodeMappingTable.COLUMN_APPLICATION_ID, data.getDataInteger(0));
        source.put(NodeMappingTable.COLUMN_ADDRESS_ID, data.getDataInteger(1));
        source.put(NodeMappingTable.COLUMN_ADDRESS, data.getDataString(1));
        source.put(NodeMappingTable.COLUMN_TIME_BUCKET, data.getDataLong(0));
        String id = data.getDataString(0);
        String sql = getBatchUpdateSql(NodeMappingTable.TABLE, source.keySet(), "id");
        entity.setSql(sql);
        List<Object> values = new ArrayList<>(source.values());
        values.add(id);
        entity.setParams(values.toArray(new Object[0]));
        return entity;
    }
}
