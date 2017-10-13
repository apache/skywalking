package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.util.TimeBucketUtils;
import org.skywalking.apm.collector.storage.define.register.InstanceDataDefine;
import org.skywalking.apm.collector.storage.define.register.InstanceTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.ui.cache.ApplicationCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;

/**
 * @author pengys5, clevertension
 */
public class InstanceH2DAO extends H2DAO implements IInstanceDAO {
    private final Logger logger = LoggerFactory.getLogger(InstanceH2DAO.class);
    private static final String GET_LAST_HEARTBEAT_TIME_SQL = "select {0} from {1} where {2} > ? limit 1";
    private static final String GET_INST_LAST_HEARTBEAT_TIME_SQL = "select {0} from {1} where {2} > ? and {3} = ? limit 1";
    private static final String GET_INSTANCE_SQL = "select * from {0} where {1} = ?";
    private static final String GET_INSTANCES_SQL = "select * from {0} where {1} = ? and {2} >= ?";
    private static final String GET_APPLICATIONS_SQL = "select {3}, count({0}) as cnt from {1} where {2} >= ? and {2} <= ? group by {3} limit 100";

    @Override
    public Long lastHeartBeatTime() {
        H2Client client = getClient();
        long fiveMinuteBefore = System.currentTimeMillis() - 5 * 60 * 1000;
        fiveMinuteBefore = TimeBucketUtils.INSTANCE.getSecondTimeBucket(fiveMinuteBefore);
        String sql = MessageFormat.format(GET_LAST_HEARTBEAT_TIME_SQL, InstanceTable.COLUMN_HEARTBEAT_TIME, InstanceTable.TABLE, InstanceTable.COLUMN_HEARTBEAT_TIME);
        Object[] params = new Object[]{fiveMinuteBefore};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0L;
    }

    @Override
    public Long instanceLastHeartBeatTime(long applicationInstanceId) {
        H2Client client = getClient();
        long fiveMinuteBefore = System.currentTimeMillis() - 5 * 60 * 1000;
        fiveMinuteBefore = TimeBucketUtils.INSTANCE.getSecondTimeBucket(fiveMinuteBefore);
        String sql = MessageFormat.format(GET_INST_LAST_HEARTBEAT_TIME_SQL, InstanceTable.COLUMN_HEARTBEAT_TIME, InstanceTable.TABLE,
                InstanceTable.COLUMN_HEARTBEAT_TIME, InstanceTable.COLUMN_INSTANCE_ID);
        Object[] params = new Object[]{fiveMinuteBefore, applicationInstanceId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getLong(1);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0L;
    }

    @Override
    public JsonArray getApplications(long startTime, long endTime) {
        H2Client client = getClient();
        JsonArray applications = new JsonArray();
        String sql = MessageFormat.format(GET_APPLICATIONS_SQL, InstanceTable.COLUMN_INSTANCE_ID,
                InstanceTable.TABLE, InstanceTable.COLUMN_HEARTBEAT_TIME, InstanceTable.COLUMN_APPLICATION_ID);
        Object[] params = new Object[]{startTime, endTime};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                Integer applicationId = rs.getInt(InstanceTable.COLUMN_APPLICATION_ID);
                logger.debug("applicationId: {}", applicationId);
                JsonObject application = new JsonObject();
                application.addProperty("applicationId", applicationId);
                application.addProperty("applicationCode", ApplicationCache.getForUI(applicationId));
                application.addProperty("instanceCount", rs.getInt("cnt"));
                applications.add(application);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return applications;
    }

    @Override
    public InstanceDataDefine.Instance getInstance(int instanceId) {
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_INSTANCE_SQL, InstanceTable.TABLE, InstanceTable.COLUMN_INSTANCE_ID);
        Object[] params = new Object[]{instanceId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                InstanceDataDefine.Instance instance = new InstanceDataDefine.Instance();
                instance.setId(String.valueOf(instanceId));
                instance.setApplicationId(rs.getInt(InstanceTable.COLUMN_APPLICATION_ID));
                instance.setAgentUUID(rs.getString(InstanceTable.COLUMN_AGENT_UUID));
                instance.setRegisterTime(rs.getLong(InstanceTable.COLUMN_REGISTER_TIME));
                instance.setHeartBeatTime(rs.getLong(InstanceTable.COLUMN_HEARTBEAT_TIME));
                instance.setOsInfo(rs.getString(InstanceTable.COLUMN_OS_INFO));
                return instance;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public List<InstanceDataDefine.Instance> getInstances(int applicationId, long timeBucket) {
        logger.debug("get instances info, application id: {}, timeBucket: {}", applicationId, timeBucket);
        List<InstanceDataDefine.Instance> instanceList = new LinkedList<>();
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_INSTANCES_SQL, InstanceTable.TABLE, InstanceTable.COLUMN_APPLICATION_ID, InstanceTable.COLUMN_HEARTBEAT_TIME);
        Object[] params = new Object[]{applicationId, timeBucket};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                InstanceDataDefine.Instance instance = new InstanceDataDefine.Instance();
                instance.setApplicationId(rs.getInt(InstanceTable.COLUMN_APPLICATION_ID));
                instance.setHeartBeatTime(rs.getLong(InstanceTable.COLUMN_HEARTBEAT_TIME));
                instance.setInstanceId(rs.getInt(InstanceTable.COLUMN_INSTANCE_ID));
                instanceList.add(instance);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return instanceList;
    }
}
