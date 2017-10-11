package org.skywalking.apm.collector.agentjvm.worker.gc.dao;

import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.jvm.GCMetricTable;
import org.skywalking.apm.collector.storage.define.jvm.MemoryMetricTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class GCMetricH2DAO extends H2DAO implements IGCMetricDAO, IPersistenceDAO<Map<String, Object>, Map<String, Object>> {
    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }

    @Override public  Map<String, Object> prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(GCMetricTable.COLUMN_INSTANCE_ID, data.getDataInteger(0));
        source.put(GCMetricTable.COLUMN_PHRASE, data.getDataInteger(1));
        source.put(GCMetricTable.COLUMN_COUNT, data.getDataLong(0));
        source.put(GCMetricTable.COLUMN_TIME, data.getDataLong(1));
        source.put(GCMetricTable.COLUMN_TIME_BUCKET, data.getDataLong(2));

        return source;
    }

    @Override public  Map<String, Object> prepareBatchUpdate(Data data) {
        return null;
    }
}
