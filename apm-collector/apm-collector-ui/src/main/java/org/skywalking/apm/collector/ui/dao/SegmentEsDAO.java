package org.skywalking.apm.collector.ui.dao;

import com.google.protobuf.InvalidProtocolBufferException;
import java.util.Base64;
import java.util.Map;
import org.elasticsearch.action.get.GetResponse;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.storage.define.segment.SegmentTable;
import org.skywalking.apm.network.proto.TraceSegmentObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class SegmentEsDAO extends EsDAO implements ISegmentDAO {

    private final Logger logger = LoggerFactory.getLogger(SegmentEsDAO.class);

    @Override public TraceSegmentObject load(String segmentId) {
        GetResponse response = getClient().prepareGet(SegmentTable.TABLE, segmentId).get();
        Map<String, Object> source = response.getSource();
        String dataBinaryBase64 = (String)source.get(SegmentTable.COLUMN_DATA_BINARY);
        if (StringUtils.isNotEmpty(dataBinaryBase64)) {
            byte[] dataBinary = Base64.getDecoder().decode(dataBinaryBase64);
            try {
                return TraceSegmentObject.parseFrom(dataBinary);
            } catch (InvalidProtocolBufferException e) {
                logger.error(e.getMessage(), e);
            }
        }
        return null;
    }
}
