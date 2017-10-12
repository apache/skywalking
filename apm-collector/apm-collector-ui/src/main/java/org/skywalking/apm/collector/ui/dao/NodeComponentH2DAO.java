package org.skywalking.apm.collector.ui.dao;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.define.node.NodeComponentTable;
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
public class NodeComponentH2DAO extends H2DAO implements INodeComponentDAO {
    private final Logger logger = LoggerFactory.getLogger(NodeComponentH2DAO.class);
    private static final String AGGREGATE_COMPONENT_SQL = "select * from {3} where {4} >= ? and {4} <= ? group by {0}, {1}, {2} limit 100";
    @Override public JsonArray load(long startTime, long endTime) {
        JsonArray nodeComponentArray = new JsonArray();
        nodeComponentArray.addAll(aggregationComponent(startTime, endTime));
        return nodeComponentArray;
    }
    private JsonArray aggregationComponent(long startTime, long endTime) {
        H2Client client = getClient();

        JsonArray nodeComponentArray = new JsonArray();
        String sql = MessageFormat.format(AGGREGATE_COMPONENT_SQL, NodeComponentTable.COLUMN_COMPONENT_ID,
                NodeComponentTable.COLUMN_PEER, NodeComponentTable.COLUMN_PEER_ID,
                NodeComponentTable.TABLE, NodeComponentTable.COLUMN_TIME_BUCKET);
        Object[] params = new Object[]{startTime, endTime};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                int peerId = rs.getInt(NodeComponentTable.COLUMN_PEER_ID);
                String componentName = rs.getString(NodeComponentTable.COLUMN_COMPONENT_NAME);
                if (peerId != 0) {
                    String peer = ApplicationCache.getForUI(peerId);
                    nodeComponentArray.add(buildNodeComponent(peer, componentName));
                }
                String peer = rs.getString(NodeComponentTable.COLUMN_PEER);
                if (StringUtils.isNotEmpty(peer)) {
                    nodeComponentArray.add(buildNodeComponent(peer, componentName));
                }
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return nodeComponentArray;
    }

    private JsonObject buildNodeComponent(String peer, String componentName) {
        JsonObject nodeComponentObj = new JsonObject();
        nodeComponentObj.addProperty("componentName", componentName);
        nodeComponentObj.addProperty("peer", peer);
        return nodeComponentObj;
    }
}
