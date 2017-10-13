package org.skywalking.apm.collector.agentjvm.worker.heartbeat.dao;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.framework.UnexpectedException;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.register.InstanceTable;
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
public class InstanceHeartBeatH2DAO extends H2DAO implements IInstanceHeartBeatDAO, IPersistenceDAO<H2SqlEntity, H2SqlEntity> {
    private final Logger logger = LoggerFactory.getLogger(InstanceHeartBeatEsDAO.class);
    private static final String GET_INSTANCE_HEARTBEAT_SQL = "select * from {0} where {1} = ?";
    @Override public Data get(String id, DataDefine dataDefine) {
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_INSTANCE_HEARTBEAT_SQL, InstanceTable.TABLE, InstanceTable.COLUMN_INSTANCE_ID);
        Object[] params = new Object[]{id};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                Data data = dataDefine.build(id);
                data.setDataInteger(0, rs.getInt(InstanceTable.COLUMN_INSTANCE_ID));
                data.setDataLong(0, rs.getLong(InstanceTable.COLUMN_HEARTBEAT_TIME));
                return data;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(Data data) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override public H2SqlEntity prepareBatchUpdate(Data data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceTable.COLUMN_HEARTBEAT_TIME, data.getDataLong(0));
        String sql = getBatchUpdateSql(InstanceTable.TABLE, source.keySet(), InstanceTable.COLUMN_APPLICATION_ID);
        entity.setSql(sql);
        List<Object> params = new ArrayList<>(source.values());
        params.add(data.getDataString(0));
        entity.setParams(params.toArray(new Object[0]));
        return entity;
    }
}
