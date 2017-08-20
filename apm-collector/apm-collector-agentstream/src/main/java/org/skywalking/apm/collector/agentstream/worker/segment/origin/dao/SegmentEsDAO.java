package org.skywalking.apm.collector.agentstream.worker.segment.origin.dao;

import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.storage.define.segment.SegmentTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class SegmentEsDAO extends EsDAO implements ISegmentDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    private final Logger logger = LoggerFactory.getLogger(SegmentEsDAO.class);

    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(SegmentTable.COLUMN_DATA_BINARY, new String(Base64.getEncoder().encode(data.getDataBytes(0))));
        logger.debug("segment source: {}", source.toString());
        return getClient().prepareIndex(SegmentTable.TABLE, data.getDataString(0)).setSource(source);
    }
}
