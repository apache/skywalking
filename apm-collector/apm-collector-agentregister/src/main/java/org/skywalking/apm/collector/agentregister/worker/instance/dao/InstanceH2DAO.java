package org.skywalking.apm.collector.agentregister.worker.instance.dao;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;
import org.skywalking.apm.collector.storage.define.register.InstanceTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5, clevertension
 */
public class InstanceH2DAO extends H2DAO implements IInstanceDAO {
    private final Logger logger = LoggerFactory.getLogger(InstanceH2DAO.class);
    private static final String GET_INSTANCE_ID_SQL = "select {0} from {1} where {2} = ? and {3} = ?";
    private static final String GET_APPLICATION_ID_SQL = "select {0} from {1} where {2} = ?";
    private static final String UPDATE_HEARTBEAT_TIME_SQL = "updatte {0} set {1} = ? where {2} = ?";
    @Override public int getInstanceId(int applicationId, String agentUUID) {
        logger.info("get the application id with application id = {}, agentUUID = {}", applicationId, agentUUID);
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_INSTANCE_ID_SQL, InstanceTable.COLUMN_INSTANCE_ID, InstanceTable.TABLE, InstanceTable.COLUMN_APPLICATION_ID,
                InstanceTable.COLUMN_AGENT_UUID);
        Object[] params = new Object[]{applicationId, agentUUID};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getInt(InstanceTable.COLUMN_INSTANCE_ID);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }

    @Override public int getMaxInstanceId() {
        return getMaxId(InstanceTable.TABLE, InstanceTable.COLUMN_INSTANCE_ID);
    }

    @Override public int getMinInstanceId() {
        return getMinId(InstanceTable.TABLE, InstanceTable.COLUMN_INSTANCE_ID);
    }

    @Override public void save(InstanceDataDefine.Instance instance) {
        H2Client client = getClient();
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceTable.COLUMN_INSTANCE_ID, instance.getInstanceId());
        source.put(InstanceTable.COLUMN_APPLICATION_ID, instance.getApplicationId());
        source.put(InstanceTable.COLUMN_AGENT_UUID, instance.getAgentUUID());
        source.put(InstanceTable.COLUMN_REGISTER_TIME, instance.getRegisterTime());
        source.put(InstanceTable.COLUMN_HEARTBEAT_TIME, instance.getHeartBeatTime());
        source.put(InstanceTable.COLUMN_OS_INFO, instance.getOsInfo());
        String sql = getBatchInsertSql(InstanceTable.TABLE, source.keySet());
        Object[] params = source.values().toArray(new Object[0]);
        try {
            client.execute(sql, params);
        } catch (H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override public void updateHeartbeatTime(int instanceId, long heartbeatTime) {
        H2Client client = getClient();
        String sql = MessageFormat.format(UPDATE_HEARTBEAT_TIME_SQL, InstanceTable.TABLE, InstanceTable.COLUMN_HEARTBEAT_TIME,
                InstanceTable.COLUMN_INSTANCE_ID);
        Object[] params = new Object[] {heartbeatTime, instanceId};
        try {
            client.execute(sql, params);
        } catch (H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override public int getApplicationId(int applicationInstanceId) {
        logger.info("get the application id with application id = {}", applicationInstanceId);
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_APPLICATION_ID_SQL, InstanceTable.COLUMN_APPLICATION_ID, InstanceTable.TABLE, InstanceTable.COLUMN_APPLICATION_ID);
        Object[] params = new Object[]{applicationInstanceId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getInt(InstanceTable.COLUMN_APPLICATION_ID);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }
}
