package org.skywalking.apm.collector.agentstream.worker.serviceref.dao;

import org.skywalking.apm.collector.storage.h2.dao.H2DAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;

/**
 * @author pengys5
 */
public class ServiceReferenceH2DAO extends H2DAO implements IServiceReferenceDAO, IPersistenceDAO<String, String> {

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
