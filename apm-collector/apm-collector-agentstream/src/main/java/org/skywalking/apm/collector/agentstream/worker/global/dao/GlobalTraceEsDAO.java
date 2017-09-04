package org.skywalking.apm.collector.agentstream.worker.global.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.core.framework.UnexpectedException;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.storage.define.global.GlobalTraceTable;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class GlobalTraceEsDAO extends EsDAO implements IGlobalTraceDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    private final Logger logger = LoggerFactory.getLogger(GlobalTraceEsDAO.class);

    @Override public Data get(String id, DataDefine dataDefine) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        throw new UnexpectedException("There is no need to merge stream data with database data.");
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(GlobalTraceTable.COLUMN_SEGMENT_ID, data.getDataString(1));
        source.put(GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID, data.getDataString(2));
        source.put(GlobalTraceTable.COLUMN_TIME_BUCKET, data.getDataLong(0));
        logger.debug("global trace source: {}", source.toString());
        return getClient().prepareIndex(GlobalTraceTable.TABLE, data.getDataString(0)).setSource(source);
    }
}
