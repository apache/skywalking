package org.skywalking.apm.collector.agentjvm.worker.gc.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.storage.table.jvm.GCMetricTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;

/**
 * @author pengys5
 */
public class GCMetricEsDAO extends EsDAO implements IGCMetricDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(GCMetricTable.COLUMN_APPLICATION_INSTANCE_ID, data.getDataInteger(0));
        source.put(GCMetricTable.COLUMN_PHRASE, data.getDataInteger(0));
        source.put(GCMetricTable.COLUMN_COUNT, data.getDataLong(0));
        source.put(GCMetricTable.COLUMN_TIME, data.getDataLong(1));
        source.put(GCMetricTable.COLUMN_TIME_BUCKET, data.getDataLong(2));

        return getClient().prepareIndex(GCMetricTable.TABLE, data.getDataString(0)).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        return null;
    }
}
