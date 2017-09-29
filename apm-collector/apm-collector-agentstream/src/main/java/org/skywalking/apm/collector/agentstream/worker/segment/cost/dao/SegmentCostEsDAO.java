package org.skywalking.apm.collector.agentstream.worker.segment.cost.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.segment.SegmentCostTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class SegmentCostEsDAO extends EsDAO implements ISegmentCostDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    private final Logger logger = LoggerFactory.getLogger(SegmentCostEsDAO.class);

    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        logger.debug("segment cost prepareBatchInsert, id: {}", data.getDataString(0));
        Map<String, Object> source = new HashMap<>();
        source.put(SegmentCostTable.COLUMN_SEGMENT_ID, data.getDataString(1));
        source.put(SegmentCostTable.COLUMN_APPLICATION_ID, data.getDataInteger(0));
        source.put(SegmentCostTable.COLUMN_SERVICE_NAME, data.getDataString(2));
        source.put(SegmentCostTable.COLUMN_COST, data.getDataLong(0));
        source.put(SegmentCostTable.COLUMN_START_TIME, data.getDataLong(1));
        source.put(SegmentCostTable.COLUMN_END_TIME, data.getDataLong(2));
        source.put(SegmentCostTable.COLUMN_IS_ERROR, data.getDataBoolean(0));
        source.put(SegmentCostTable.COLUMN_TIME_BUCKET, data.getDataLong(3));
        logger.debug("segment cost source: {}", source.toString());
        return getClient().prepareIndex(SegmentCostTable.TABLE, data.getDataString(0)).setSource(source);
    }
}
