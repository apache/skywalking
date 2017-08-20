package org.skywalking.apm.collector.agentstream.worker.noderef.reference.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.storage.define.noderef.NodeRefTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;

/**
 * @author pengys5
 */
public class NodeReferenceEsDAO extends EsDAO implements INodeReferenceDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    @Override public Data get(String id, DataDefine dataDefine) {
        GetResponse getResponse = getClient().prepareGet(NodeRefTable.TABLE, id).get();
        if (getResponse.isExists()) {
            Data data = dataDefine.build(id);
            Map<String, Object> source = getResponse.getSource();
            data.setDataString(1, (String)source.get(NodeRefTable.COLUMN_AGG));
            data.setDataLong(0, (Long)source.get(NodeRefTable.COLUMN_TIME_BUCKET));
            return data;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(NodeRefTable.COLUMN_AGG, data.getDataString(1));
        source.put(NodeRefTable.COLUMN_TIME_BUCKET, data.getDataLong(0));

        return getClient().prepareIndex(NodeRefTable.TABLE, data.getDataString(0)).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(NodeRefTable.COLUMN_AGG, data.getDataString(1));
        source.put(NodeRefTable.COLUMN_TIME_BUCKET, data.getDataLong(0));

        return getClient().prepareUpdate(NodeRefTable.TABLE, data.getDataString(0)).setDoc(source);
    }
}
