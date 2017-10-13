package org.skywalking.apm.collector.agentregister.worker.application.dao;

import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.storage.define.register.ApplicationDataDefine;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.storage.define.register.ApplicationTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.MessageFormat;

/**
 * @author pengys5
 */
public class ApplicationH2DAO extends H2DAO implements IApplicationDAO {
    private final Logger logger = LoggerFactory.getLogger(ApplicationH2DAO.class);
    private static final String INSERT_APPLICATION_SQL = "insert into {0}({1}, {2}) values(?, ?)";
    @Override
    public int getApplicationId(String applicationCode) {
        logger.info("get the application id with application code = {}", applicationCode);
        String sql = "select " + ApplicationTable.COLUMN_APPLICATION_ID + " from " +
                ApplicationTable.TABLE + " where " + ApplicationTable.COLUMN_APPLICATION_CODE + "='" + applicationCode + "'";

        return getIntValueBySQL(sql);
    }

    @Override
    public int getMaxApplicationId() {
        return getMaxId(ApplicationTable.TABLE, ApplicationTable.COLUMN_APPLICATION_ID);
    }

    @Override
    public int getMinApplicationId() {
        return getMinId(ApplicationTable.TABLE, ApplicationTable.COLUMN_APPLICATION_ID);
    }

    @Override
    public void save(ApplicationDataDefine.Application application) {
        H2Client client = getClient();
        String sql = MessageFormat.format(INSERT_APPLICATION_SQL, ApplicationTable.TABLE, ApplicationTable.COLUMN_APPLICATION_ID,
                ApplicationTable.COLUMN_APPLICATION_CODE);
        Object[] params = new Object[] {application.getApplicationId(), application.getApplicationCode()};
        try {
            client.execute(sql, params);
        } catch (H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
