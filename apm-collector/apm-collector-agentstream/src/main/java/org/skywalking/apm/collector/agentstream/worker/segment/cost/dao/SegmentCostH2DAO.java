package org.skywalking.apm.collector.agentstream.worker.segment.cost.dao;

import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.segment.SegmentCostTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.define.H2SqlEntity;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5, clevertension
 */
public class SegmentCostH2DAO extends H2DAO implements ISegmentCostDAO, IPersistenceDAO<H2SqlEntity, H2SqlEntity> {
    private final Logger logger = LoggerFactory.getLogger(SegmentCostH2DAO.class);
    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }
    @Override public H2SqlEntity prepareBatchInsert(Data data) {
        logger.debug("segment cost prepareBatchInsert, id: {}", data.getDataString(0));
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put("id", data.getDataString(0));
        source.put(SegmentCostTable.COLUMN_SEGMENT_ID, data.getDataString(1));
        source.put(SegmentCostTable.COLUMN_APPLICATION_ID, data.getDataInteger(0));
        source.put(SegmentCostTable.COLUMN_SERVICE_NAME, data.getDataString(2));
        source.put(SegmentCostTable.COLUMN_COST, data.getDataLong(0));
        source.put(SegmentCostTable.COLUMN_START_TIME, data.getDataLong(1));
        source.put(SegmentCostTable.COLUMN_END_TIME, data.getDataLong(2));
        source.put(SegmentCostTable.COLUMN_IS_ERROR, data.getDataBoolean(0));
        source.put(SegmentCostTable.COLUMN_TIME_BUCKET, data.getDataLong(3));
        logger.debug("segment cost source: {}", source.toString());

        String sql = getBatchInsertSql(SegmentCostTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }
    @Override public H2SqlEntity prepareBatchUpdate(Data data) {
        return null;
    }
}
