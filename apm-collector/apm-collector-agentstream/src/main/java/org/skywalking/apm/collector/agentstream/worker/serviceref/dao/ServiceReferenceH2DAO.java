package org.skywalking.apm.collector.agentstream.worker.serviceref.dao;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.storage.define.register.InstanceTable;
import org.skywalking.apm.collector.storage.define.serviceref.ServiceReferenceTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class ServiceReferenceH2DAO extends H2DAO implements IServiceReferenceDAO, IPersistenceDAO<Map<String, Object>, Map<String, Object>> {
    private final Logger logger = LoggerFactory.getLogger(ServiceReferenceH2DAO.class);
    @Override public Data get(String id, DataDefine dataDefine) {
        H2Client client = getClient();
        String sql = "select * from " + ServiceReferenceTable.TABLE + " where " + ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID + "=?";
        Object[] params = new Object[] {id};
        ResultSet rs = null;
        try {
            rs = client.executeQuery(sql, params);
            Data data = dataDefine.build(id);
            data.setDataInteger(0, rs.getInt(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID));
            data.setDataString(1, rs.getString(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME));
            data.setDataInteger(1, rs.getInt(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID));
            data.setDataString(2, rs.getString(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME));
            data.setDataInteger(2, rs.getInt(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID));
            data.setDataString(3, rs.getString(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME));
            data.setDataLong(0, rs.getLong(ServiceReferenceTable.COLUMN_S1_LTE));
            data.setDataLong(1, rs.getLong(ServiceReferenceTable.COLUMN_S3_LTE));
            data.setDataLong(2, rs.getLong(ServiceReferenceTable.COLUMN_S5_LTE));
            data.setDataLong(3, rs.getLong(ServiceReferenceTable.COLUMN_S5_GT));
            data.setDataLong(4, rs.getLong(ServiceReferenceTable.COLUMN_SUMMARY));
            data.setDataLong(5, rs.getLong(ServiceReferenceTable.COLUMN_ERROR));
            data.setDataLong(6, rs.getLong(ServiceReferenceTable.COLUMN_COST_SUMMARY));
            data.setDataLong(7, rs.getLong(ServiceReferenceTable.COLUMN_TIME_BUCKET));
            return data;
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        } finally {
            client.closeResultSet(rs);
        }
        return null;
    }

    @Override public Map<String, Object> prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, data.getDataInteger(0));
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, data.getDataString(1));
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, data.getDataInteger(1));
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, data.getDataString(2));
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID, data.getDataInteger(2));
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME, data.getDataString(3));
        source.put(ServiceReferenceTable.COLUMN_S1_LTE, data.getDataLong(0));
        source.put(ServiceReferenceTable.COLUMN_S3_LTE, data.getDataLong(1));
        source.put(ServiceReferenceTable.COLUMN_S5_LTE, data.getDataLong(2));
        source.put(ServiceReferenceTable.COLUMN_S5_GT, data.getDataLong(3));
        source.put(ServiceReferenceTable.COLUMN_SUMMARY, data.getDataLong(4));
        source.put(ServiceReferenceTable.COLUMN_ERROR, data.getDataLong(5));
        source.put(ServiceReferenceTable.COLUMN_COST_SUMMARY, data.getDataLong(6));
        source.put(ServiceReferenceTable.COLUMN_TIME_BUCKET, data.getDataLong(7));

        return source;
    }

    @Override public Map<String, Object> prepareBatchUpdate(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, data.getDataInteger(0));
        source.put(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, data.getDataString(1));
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, data.getDataInteger(1));
        source.put(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, data.getDataString(2));
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID, data.getDataInteger(2));
        source.put(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME, data.getDataString(3));
        source.put(ServiceReferenceTable.COLUMN_S1_LTE, data.getDataLong(0));
        source.put(ServiceReferenceTable.COLUMN_S3_LTE, data.getDataLong(1));
        source.put(ServiceReferenceTable.COLUMN_S5_LTE, data.getDataLong(2));
        source.put(ServiceReferenceTable.COLUMN_S5_GT, data.getDataLong(3));
        source.put(ServiceReferenceTable.COLUMN_SUMMARY, data.getDataLong(4));
        source.put(ServiceReferenceTable.COLUMN_ERROR, data.getDataLong(5));
        source.put(ServiceReferenceTable.COLUMN_COST_SUMMARY, data.getDataLong(6));
        source.put(ServiceReferenceTable.COLUMN_TIME_BUCKET, data.getDataLong(7));

        return source;
    }
}
