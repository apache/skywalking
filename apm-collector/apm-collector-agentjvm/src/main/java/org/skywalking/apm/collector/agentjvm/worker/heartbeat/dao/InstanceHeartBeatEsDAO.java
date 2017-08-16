package org.skywalking.apm.collector.agentjvm.worker.heartbeat.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.storage.table.register.InstanceTable;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;
import org.skywalking.apm.collector.stream.worker.impl.data.DataDefine;

/**
 * @author pengys5
 */
public class InstanceHeartBeatEsDAO extends EsDAO implements IInstanceHeartBeatDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    @Override public Data get(String id, DataDefine dataDefine) {
        return null;
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        return null;
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(InstanceTable.COLUMN_REGISTER_TIME, data.getDataLong(0));
        return getClient().prepareUpdate(InstanceTable.TABLE, data.getDataString(0)).setDoc(source);
    }
}
