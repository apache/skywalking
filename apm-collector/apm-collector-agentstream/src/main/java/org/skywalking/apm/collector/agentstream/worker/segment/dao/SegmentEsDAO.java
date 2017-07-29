package org.skywalking.apm.collector.agentstream.worker.segment.dao;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.skywalking.apm.collector.agentstream.worker.segment.define.SegmentTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class SegmentEsDAO extends EsDAO implements ISegmentDAO {

    private final Logger logger = LoggerFactory.getLogger(SegmentEsDAO.class);

    @Override public List<?> prepareBatch(Map<String, Data> dataMap) {
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        dataMap.forEach((id, data) -> {
            logger.debug("segment prepareBatch, id: {}", id);
            Map<String, Object> source = new HashMap();
            source.put(SegmentTable.COLUMN_DATA_BINARY, new String(Base64.getEncoder().encode(data.getDataBytes(0))));
            logger.debug("segment source: {}", source.toString());
            IndexRequestBuilder builder = getClient().prepareIndex(SegmentTable.TABLE, id).setSource(source);
            indexRequestBuilders.add(builder);
        });
        return indexRequestBuilders;
    }
}
