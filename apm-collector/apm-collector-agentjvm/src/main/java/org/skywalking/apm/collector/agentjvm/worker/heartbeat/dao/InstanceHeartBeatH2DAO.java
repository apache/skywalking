package org.skywalking.apm.collector.agentjvm.worker.heartbeat.dao;

import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.framework.UnexpectedException;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.register.InstanceTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class InstanceHeartBeatH2DAO extends H2DAO implements IInstanceHeartBeatDAO, IPersistenceDAO<Map<String, Object>, Map<String, Object>> {
    private final Logger logger = LoggerFactory.getLogger(InstanceHeartBeatEsDAO.class);

    @Override public Data get(String id, DataDefine dataDefine) {
        H2Client client = getClient();
        String sql = "select " + InstanceTable.COLUMN_INSTANCE_ID + "," + InstanceTable.COLUMN_HEARTBEAT_TIME +
                " from " + InstanceTable.TABLE + " where " + InstanceTable.COLUMN_INSTANCE_ID + "=?";
        Object[] params = new Object[] {id};
        ResultSet rs = null;
        try {
            rs = client.executeQuery(sql, params);
            Data data = dataDefine.build(id);
            data.setDataInteger(0, rs.getInt(1));
            data.setDataLong(0, rs.getLong(2));
            return data;
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        } finally {
            client.closeResultSet(rs);
        }
        return null;
    }

    @Override public Map<String, Object> prepareBatchInsert(Data data) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override public Map<String, Object> prepareBatchUpdate(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceTable.COLUMN_HEARTBEAT_TIME, data.getDataLong(0));
        return source;
    }
}
