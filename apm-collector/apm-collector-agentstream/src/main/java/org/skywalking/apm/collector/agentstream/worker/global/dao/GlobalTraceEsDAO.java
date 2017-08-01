package org.skywalking.apm.collector.agentstream.worker.global.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.skywalking.apm.collector.agentstream.worker.global.define.GlobalTraceTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class GlobalTraceEsDAO extends EsDAO implements IGlobalTraceDAO {

    private final Logger logger = LoggerFactory.getLogger(GlobalTraceEsDAO.class);

    @Override public List<?> prepareBatch(Map<String, Data> dataMap) {
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        dataMap.forEach((id, data) -> {
            logger.debug("global trace prepareBatch, id: {}", id);
            Map<String, Object> source = new HashMap();
            source.put(GlobalTraceTable.COLUMN_SEGMENT_ID, data.getDataString(1));
            source.put(GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID, data.getDataString(2));
            source.put(GlobalTraceTable.COLUMN_TIME_BUCKET, data.getDataLong(0));
            logger.debug("global trace source: {}", source.toString());
            IndexRequestBuilder builder = getClient().prepareIndex(GlobalTraceTable.TABLE, id).setSource(source);
            indexRequestBuilders.add(builder);
        });
        return indexRequestBuilders;
    }
}
