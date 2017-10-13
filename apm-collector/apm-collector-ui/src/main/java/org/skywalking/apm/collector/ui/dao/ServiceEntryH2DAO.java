package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.util.ColumnNameUtils;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.define.service.ServiceEntryTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.ui.cache.ApplicationCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5
 */
public class ServiceEntryH2DAO extends H2DAO implements IServiceEntryDAO {
    private final Logger logger = LoggerFactory.getLogger(SegmentH2DAO.class);
    private static final String GET_SERVICE_ENTRY_SQL = "select * from {0} where {1} >= ? and {2} <= ?";
    @Override public JsonObject load(int applicationId, String entryServiceName, long startTime, long endTime, int from,
        int size) {
        H2Client client = getClient();
        String sql = GET_SERVICE_ENTRY_SQL;
        List<Object> params = new ArrayList<>();
        List<String> columns = new ArrayList<>();
        params.add(startTime);
        params.add(endTime);
        int paramIndex = 2;
        if (applicationId != 0) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} = ?";
            params.add(applicationId);
            columns.add(ServiceEntryTable.COLUMN_APPLICATION_ID);
        }
        if (StringUtils.isNotEmpty(entryServiceName)) {
            paramIndex++;
            sql = sql + " and {" + paramIndex + "} = ?";
            params.add(entryServiceName);
            columns.add(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME);
        }
        sql = sql + " limit " + from + "," + size;
        sql = MessageFormat.format(sql, ServiceEntryTable.TABLE, ServiceEntryTable.COLUMN_NEWEST_TIME,
                ServiceEntryTable.COLUMN_REGISTER_TIME, columns);
        Object[] p = params.toArray(new Object[0]);
        JsonArray serviceArray = new JsonArray();
        JsonObject response = new JsonObject();
        int index = 0;
        try (ResultSet rs = client.executeQuery(sql, p)) {
            while (rs.next()) {
                int appId = rs.getInt(ServiceEntryTable.COLUMN_APPLICATION_ID);
                int entryServiceId = rs.getInt(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID);
                String applicationCode = ApplicationCache.getForUI(applicationId);
                String entryServiceName1 = rs.getString(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME);

                JsonObject row = new JsonObject();
                row.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_ENTRY_SERVICE_ID), entryServiceId);
                row.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_ENTRY_SERVICE_NAME), entryServiceName1);
                row.addProperty(ColumnNameUtils.INSTANCE.rename(ServiceEntryTable.COLUMN_APPLICATION_ID), appId);
                row.addProperty("applicationCode", applicationCode);
                serviceArray.add(row);
                index++;
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        response.addProperty("total", index);
        response.add("array", serviceArray);

        return response;
    }
}
