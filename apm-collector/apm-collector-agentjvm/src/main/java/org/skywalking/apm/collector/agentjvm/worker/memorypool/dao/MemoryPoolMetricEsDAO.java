package org.skywalking.apm.collector.agentjvm.worker.memorypool.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.storage.table.jvm.MemoryPoolMetricTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;

/**
 * @author pengys5
 */
public class MemoryPoolMetricEsDAO extends EsDAO implements IMemoryPoolMetricDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(MemoryPoolMetricTable.COLUMN_APPLICATION_INSTANCE_ID, data.getDataInteger(0));
        source.put(MemoryPoolMetricTable.COLUMN_POOL_TYPE, data.getDataInteger(1));
        source.put(MemoryPoolMetricTable.COLUMN_IS_HEAP, data.getDataBoolean(0));
        source.put(MemoryPoolMetricTable.COLUMN_INIT, data.getDataLong(0));
        source.put(MemoryPoolMetricTable.COLUMN_MAX, data.getDataLong(1));
        source.put(MemoryPoolMetricTable.COLUMN_USED, data.getDataLong(2));
        source.put(MemoryPoolMetricTable.COLUMN_COMMITTED, data.getDataLong(3));
        source.put(MemoryPoolMetricTable.COLUMN_TIME_BUCKET, data.getDataLong(4));

        return getClient().prepareIndex(MemoryPoolMetricTable.TABLE, data.getDataString(0)).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        return null;
    }
}
