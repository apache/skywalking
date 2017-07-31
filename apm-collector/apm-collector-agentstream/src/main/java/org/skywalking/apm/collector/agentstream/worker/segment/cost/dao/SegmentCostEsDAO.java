package org.skywalking.apm.collector.agentstream.worker.segment.cost.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.skywalking.apm.collector.agentstream.worker.segment.cost.define.SegmentCostTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class SegmentCostEsDAO extends EsDAO implements ISegmentCostDAO {

    private final Logger logger = LoggerFactory.getLogger(SegmentCostEsDAO.class);

    @Override public List<?> prepareBatch(Map<String, Data> dataMap) {
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        dataMap.forEach((id, data) -> {
            logger.debug("segment cost prepareBatch, id: {}", id);
            Map<String, Object> source = new HashMap();
            source.put(SegmentCostTable.COLUMN_SEGMENT_ID, data.getDataString(1));
            source.put(SegmentCostTable.COLUMN_GLOBAL_TRACE_ID, data.getDataString(2));
            source.put(SegmentCostTable.COLUMN_OPERATION_NAME, data.getDataString(3));
            source.put(SegmentCostTable.COLUMN_COST, data.getDataLong(0));
            source.put(SegmentCostTable.COLUMN_START_TIME, data.getDataLong(1));
            source.put(SegmentCostTable.COLUMN_END_TIME, data.getDataLong(2));
            source.put(SegmentCostTable.COLUMN_IS_ERROR, data.getDataBoolean(0));
            source.put(SegmentCostTable.COLUMN_TIME_BUCKET, data.getDataLong(3));
            logger.debug("segment cost source: {}", source.toString());
            IndexRequestBuilder builder = getClient().prepareIndex(SegmentCostTable.TABLE, id).setSource(source);
            indexRequestBuilders.add(builder);
        });
        return indexRequestBuilders;
    }
}
