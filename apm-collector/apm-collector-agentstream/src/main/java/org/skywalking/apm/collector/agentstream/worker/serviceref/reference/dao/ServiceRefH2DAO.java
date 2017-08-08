package org.skywalking.apm.collector.agentstream.worker.serviceref.reference.dao;

import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;

/**
 * @author pengys5
 */
public class ServiceRefH2DAO extends H2DAO implements IServiceRefDAO, IPersistenceDAO<String, String> {

    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }

    @Override public String prepareBatchInsert(Data data) {
        return null;
    }

    @Override public String prepareBatchUpdate(Data data) {
        return null;
    }
}
