package org.skywalking.apm.collector.ui.dao;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.storage.define.global.GlobalTraceTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

/**
 * @author pengys5, clevertension
 */
public class GlobalTraceH2DAO extends H2DAO implements IGlobalTraceDAO {
    private final Logger logger = LoggerFactory.getLogger(GlobalTraceH2DAO.class);
    private static final String GET_GLOBAL_TRACE_ID_SQL = "select {0} from {1} where {2} = ? limit 10";
    private static final String GET_SEGMENT_IDS_SQL = "select {0} from {1} where {2} = ? limit 10";
    @Override public List<String> getGlobalTraceId(String segmentId) {
        List<String> globalTraceIds = new ArrayList<>();
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_GLOBAL_TRACE_ID_SQL, GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID,
                GlobalTraceTable.TABLE, GlobalTraceTable.COLUMN_SEGMENT_ID);
        Object[] params = new Object[]{segmentId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                String globalTraceId = rs.getString(GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID);
                logger.debug("segmentId: {}, global trace id: {}", segmentId, globalTraceId);
                globalTraceIds.add(globalTraceId);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return globalTraceIds;
    }

    @Override public List<String> getSegmentIds(String globalTraceId) {
        List<String> segmentIds = new ArrayList<>();
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_SEGMENT_IDS_SQL, GlobalTraceTable.COLUMN_SEGMENT_ID,
                GlobalTraceTable.TABLE, GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID);
        Object[] params = new Object[]{globalTraceId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            while (rs.next()) {
                String segmentId = rs.getString(GlobalTraceTable.COLUMN_SEGMENT_ID);
                logger.debug("segmentId: {}, global trace id: {}", segmentId, globalTraceId);
                segmentIds.add(globalTraceId);
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return segmentIds;
    }
}
