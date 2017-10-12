package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.define.node.NodeMappingTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.ui.cache.ApplicationCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;

/**
 * @author pengys5
 */
public class NodeMappingH2DAO extends H2DAO implements INodeMappingDAO {
    private final Logger logger = LoggerFactory.getLogger(NodeMappingH2DAO.class);
    private static final String NODE_MAPPING_SQL = "select * from {3} where {4} >= ? and {4} <= ? group by {0}, {1}, {2} limit 100";
    @Override public JsonArray load(long startTime, long endTime) {
        H2Client client = getClient();
        JsonArray nodeMappingArray = new JsonArray();
        String sql = MessageFormat.format(NODE_MAPPING_SQL, NodeMappingTable.COLUMN_APPLICATION_ID,
                NodeMappingTable.COLUMN_ADDRESS_ID, NodeMappingTable.COLUMN_ADDRESS,
                NodeMappingTable.TABLE, NodeMappingTable.COLUMN_TIME_BUCKET);

        Object[] params = new Object[]{startTime, endTime};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                int applicationId = rs.getInt(NodeMappingTable.COLUMN_APPLICATION_ID);
                String applicationCode = ApplicationCache.getForUI(applicationId);
                int addressId = rs.getInt(NodeMappingTable.COLUMN_ADDRESS_ID);
                if (addressId != 0) {
                    String address = ApplicationCache.getForUI(addressId);
                    JsonObject nodeMappingObj = new JsonObject();
                    nodeMappingObj.addProperty("applicationCode", applicationCode);
                    nodeMappingObj.addProperty("address", address);
                    nodeMappingArray.add(nodeMappingObj);
                }
                String address = rs.getString(NodeMappingTable.COLUMN_ADDRESS);
                if (StringUtils.isNotEmpty(address)) {
                    JsonObject nodeMappingObj = new JsonObject();
                    nodeMappingObj.addProperty("applicationCode", applicationCode);
                    nodeMappingObj.addProperty("address", address);
                    nodeMappingArray.add(nodeMappingObj);
                }
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        logger.debug("node mapping data: {}", nodeMappingArray.toString());
        return nodeMappingArray;
    }
}
