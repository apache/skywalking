package org.skywalking.apm.collector.agentstream.worker.segment.cost.dao;

import org.skywalking.apm.collector.agentstream.worker.service.entry.dao.ServiceEntryH2DAO;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.service.ServiceEntryTable;
import org.skywalking.apm.collector.storage.define.serviceref.ServiceReferenceTable;
import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

/**
 * @author pengys5
 */
public class SegmentCostH2DAO extends H2DAO implements ISegmentCostDAO, IPersistenceDAO<Map<String, Object>, Map<String, Object>> {
    private final Logger logger = LoggerFactory.getLogger(SegmentCostH2DAO.class);
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
