package org.skywalking.apm.collector.agentjvm.worker.cpu.dao;

import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.jvm.CpuMetricTable;
import org.skywalking.apm.collector.storage.define.jvm.GCMetricTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class CpuMetricH2DAO extends H2DAO implements ICpuMetricDAO, IPersistenceDAO<Map<String, Object>, Map<String, Object>> {
    private final Logger logger = LoggerFactory.getLogger(CpuMetricH2DAO.class);
    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }

    @Override public  Map<String, Object> prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(CpuMetricTable.COLUMN_INSTANCE_ID, data.getDataInteger(0));
        source.put(CpuMetricTable.COLUMN_USAGE_PERCENT, data.getDataDouble(0));
        source.put(CpuMetricTable.COLUMN_TIME_BUCKET, data.getDataLong(0));

        logger.debug("prepare cpu metric batch insert, id: {}", data.getDataString(0));
        return source;
    }

    @Override public  Map<String, Object> prepareBatchUpdate(Data data) {
        return null;
    }
}
