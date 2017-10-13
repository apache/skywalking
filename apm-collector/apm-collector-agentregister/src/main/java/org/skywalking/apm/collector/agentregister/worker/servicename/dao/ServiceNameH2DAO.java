package org.skywalking.apm.collector.agentregister.worker.servicename.dao;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.define.register.ServiceNameDataDefine;
import org.skywalking.apm.collector.storage.define.register.ServiceNameTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5, clevertension
 */
public class ServiceNameH2DAO extends H2DAO implements IServiceNameDAO {
    private final Logger logger = LoggerFactory.getLogger(ServiceNameH2DAO.class);
    private static final String GET_SERVICE_ID_SQL = "select {0} from {1} where {2} = ? and {3} = ? limit 1";
    private static final String GET_SERVICE_NAME_SQL = "select {0} from {1} where {2} = ?";

    @Override
    public int getServiceId(int applicationId, String serviceName) {
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_SERVICE_ID_SQL, ServiceNameTable.COLUMN_SERVICE_ID, ServiceNameTable.COLUMN_SERVICE_NAME,
                ServiceNameTable.TABLE, ServiceNameTable.COLUMN_APPLICATION_ID, ServiceNameTable.COLUMN_SERVICE_NAME);
        Object[] params = new Object[]{applicationId, serviceName};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getInt(ServiceNameTable.COLUMN_SERVICE_ID);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }

    @Override
    public int getMaxServiceId() {
        return getMaxId(ServiceNameTable.TABLE, ServiceNameTable.COLUMN_SERVICE_ID);
    }

    @Override
    public int getMinServiceId() {
        return getMinId(ServiceNameTable.TABLE, ServiceNameTable.COLUMN_SERVICE_ID);
    }


    @Override
    public String getServiceName(int serviceId) {
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_SERVICE_NAME_SQL, ServiceNameTable.COLUMN_SERVICE_NAME,
                ServiceNameTable.TABLE, ServiceNameTable.COLUMN_SERVICE_ID);
        Object[] params = new Object[]{serviceId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                return rs.getString(ServiceNameTable.COLUMN_SERVICE_NAME);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return Const.EMPTY_STRING;
    }

    @Override
    public void save(ServiceNameDataDefine.ServiceName serviceName) {
        logger.debug("save service name register info, application id: {}, service name: {}", serviceName.getApplicationId(), serviceName.getServiceName());
        H2Client client = getClient();
        Map<String, Object> source = new HashMap();
        source.put(ServiceNameTable.COLUMN_SERVICE_ID, serviceName.getServiceId());
        source.put(ServiceNameTable.COLUMN_APPLICATION_ID, serviceName.getApplicationId());
        source.put(ServiceNameTable.COLUMN_SERVICE_NAME, serviceName.getServiceName());

        String sql = getBatchInsertSql(ServiceNameTable.TABLE, source.keySet());
        Object[] params = source.values().toArray(new Object[0]);
        try {
            client.execute(sql, params);
        } catch (H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
