package org.skywalking.apm.collector.agentstream.worker.serviceref.reference.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.storage.define.serviceref.ServiceRefTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ServiceRefEsDAO extends EsDAO implements IServiceRefDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    private final Logger logger = LoggerFactory.getLogger(ServiceRefEsDAO.class);

    @Override public Data get(String id, DataDefine dataDefine) {
        GetResponse getResponse = getClient().prepareGet(ServiceRefTable.TABLE, id).get();
        if (getResponse.isExists()) {
            Data data = dataDefine.build(id);
            Map<String, Object> source = getResponse.getSource();
            data.setDataString(1, (String)source.get(ServiceRefTable.COLUMN_ENTRY_SERVICE));
            data.setDataString(2, (String)source.get(ServiceRefTable.COLUMN_AGG));
            data.setDataLong(0, (Long)source.get(ServiceRefTable.COLUMN_TIME_BUCKET));
            return data;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceRefTable.COLUMN_ENTRY_SERVICE, data.getDataString(1));
        source.put(ServiceRefTable.COLUMN_AGG, data.getDataString(2));
        source.put(ServiceRefTable.COLUMN_TIME_BUCKET, data.getDataLong(0));

        return getClient().prepareIndex(ServiceRefTable.TABLE, data.getDataString(0)).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(ServiceRefTable.COLUMN_ENTRY_SERVICE, data.getDataString(1));
        source.put(ServiceRefTable.COLUMN_AGG, data.getDataString(2));
        source.put(ServiceRefTable.COLUMN_TIME_BUCKET, data.getDataLong(0));

        return getClient().prepareUpdate(ServiceRefTable.TABLE, data.getDataString(0)).setDoc(source);
    }
}
