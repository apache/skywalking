package org.skywalking.apm.collector.agentjvm.worker.memory.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.agentjvm.worker.memory.define.MemoryMetricTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;

/**
 * @author pengys5
 */
public class MemoryMetricEsDAO extends EsDAO implements IMemoryMetricDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(MemoryMetricTable.COLUMN_APPLICATION_INSTANCE_ID, data.getDataInteger(0));
        source.put(MemoryMetricTable.COLUMN_IS_HEAP, data.getDataBoolean(0));
        source.put(MemoryMetricTable.COLUMN_INIT, data.getDataLong(0));
        source.put(MemoryMetricTable.COLUMN_MAX, data.getDataLong(1));
        source.put(MemoryMetricTable.COLUMN_USED, data.getDataLong(2));
        source.put(MemoryMetricTable.COLUMN_COMMITTED, data.getDataLong(3));
        source.put(MemoryMetricTable.COLUMN_TIME_BUCKET, data.getDataLong(4));

        return getClient().prepareIndex(MemoryMetricTable.TABLE, data.getDataString(0)).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        return null;
    }
}
