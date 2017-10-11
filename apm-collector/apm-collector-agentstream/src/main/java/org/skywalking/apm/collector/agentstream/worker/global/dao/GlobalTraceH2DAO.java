package org.skywalking.apm.collector.agentstream.worker.global.dao;

import org.skywalking.apm.collector.agentstream.worker.instance.performance.dao.InstPerformanceH2DAO;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * @author pengys5
 */
public class GlobalTraceH2DAO extends H2DAO implements IGlobalTraceDAO, IPersistenceDAO<Map<String, Object>, Map<String, Object>> {
    private final Logger logger = LoggerFactory.getLogger(GlobalTraceH2DAO.class);
    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }
    @Override public Map<String, Object> prepareBatchInsert(Data data) {
        return null;
    }
    @Override public Map<String, Object> prepareBatchUpdate(Data data) {
        return null;
    }
}
