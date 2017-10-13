package org.skywalking.apm.collector.ui.dao;

import com.google.protobuf.InvalidProtocolBufferException;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.define.segment.SegmentTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.MessageFormat;
import java.util.Base64;

/**
 * @author pengys5, clevertension
 */
public class SegmentH2DAO extends H2DAO implements ISegmentDAO {
    private final Logger logger = LoggerFactory.getLogger(SegmentH2DAO.class);
    private static final String GET_SEGMENT_SQL = "select {0} from {1} where {2} = ?";
    @Override public TraceSegmentObject load(String segmentId) {
        H2Client client = getClient();
        String sql = MessageFormat.format(GET_SEGMENT_SQL, SegmentTable.COLUMN_DATA_BINARY,
                SegmentTable.TABLE, "id");
        Object[] params = new Object[]{segmentId};
        try (ResultSet rs = client.executeQuery(sql, params)) {
            if (rs.next()) {
                String dataBinaryBase64 = rs.getString(SegmentTable.COLUMN_DATA_BINARY);
                if (StringUtils.isNotEmpty(dataBinaryBase64)) {
                    byte[] dataBinary = Base64.getDecoder().decode(dataBinaryBase64);
                    try {
                        return TraceSegmentObject.parseFrom(dataBinary);
                    } catch (InvalidProtocolBufferException e) {
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        } catch (SQLException | H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
        return null;
    }
}
