package org.skywalking.apm.collector.agentjvm.worker.memorypool.dao;

import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.jvm.MemoryPoolMetricTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.storage.h2.define.H2SqlEntity;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class MemoryPoolMetricH2DAO extends H2DAO implements IMemoryPoolMetricDAO, IPersistenceDAO<H2SqlEntity, H2SqlEntity> {
    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }

    @Override public H2SqlEntity prepareBatchInsert(Data data) {
        H2SqlEntity entity = new H2SqlEntity();
        Map<String, Object> source = new HashMap<>();
        source.put("id", data.getDataString(0));
        source.put(MemoryPoolMetricTable.COLUMN_INSTANCE_ID, data.getDataInteger(0));
        source.put(MemoryPoolMetricTable.COLUMN_POOL_TYPE, data.getDataInteger(1));
        source.put(MemoryPoolMetricTable.COLUMN_INIT, data.getDataLong(0));
        source.put(MemoryPoolMetricTable.COLUMN_MAX, data.getDataLong(1));
        source.put(MemoryPoolMetricTable.COLUMN_USED, data.getDataLong(2));
        source.put(MemoryPoolMetricTable.COLUMN_COMMITTED, data.getDataLong(3));
        source.put(MemoryPoolMetricTable.COLUMN_TIME_BUCKET, data.getDataLong(4));

        String sql = getBatchInsertSql(MemoryPoolMetricTable.TABLE, source.keySet());
        entity.setSql(sql);
        entity.setParams(source.values().toArray(new Object[0]));
        return entity;
    }

    @Override public H2SqlEntity prepareBatchUpdate(Data data) {
        return null;
    }
}
