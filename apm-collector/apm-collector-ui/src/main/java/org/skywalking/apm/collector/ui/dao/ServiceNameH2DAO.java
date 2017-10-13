package org.skywalking.apm.collector.ui.dao;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.util.Const;
import org.skywalking.apm.collector.storage.define.register.ServiceNameTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;

/**
 * @author pengys5, clevertension
 */
public class ServiceNameH2DAO extends H2DAO implements IServiceNameDAO {
    private final Logger logger = LoggerFactory.getLogger(ServiceNameH2DAO.class);
    private static final String GET_SERVICE_NAME_SQL = "select {0} from {1} where {2} = ?";
    private static final String GET_SERVICE_ID_SQL = "select {0} from {1} where {2} = ? and {3} = ? limit 1";
    @Override public String getServiceName(int serviceId) {
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
        return Const.UNKNOWN;
    }

    @Override public int getServiceId(int applicationId, String serviceName) {
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_SERVICE_ID_SQL, ServiceNameTable.COLUMN_SERVICE_ID,
                ServiceNameTable.TABLE, ServiceNameTable.COLUMN_APPLICATION_ID, ServiceNameTable.COLUMN_SERVICE_NAME);
        Object[] params = new Object[]{applicationId, serviceName};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                int serviceId = rs.getInt(ServiceNameTable.COLUMN_SERVICE_ID);
                return serviceId;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return 0;
    }
}
