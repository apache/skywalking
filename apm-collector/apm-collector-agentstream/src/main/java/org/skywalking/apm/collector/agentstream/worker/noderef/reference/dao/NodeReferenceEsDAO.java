package org.skywalking.apm.collector.agentstream.worker.noderef.reference.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.skywalking.apm.collector.agentstream.worker.noderef.reference.define.NodeRefTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;

/**
 * @author pengys5
 */
public class NodeReferenceEsDAO extends EsDAO implements INodeReferenceDAO {

    @Override public List<?> prepareBatch(Map<String, Data> dataMap) {
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        dataMap.forEach((id, data) -> {
            Map<String, Object> source = new HashMap();
            source.put(NodeRefTable.COLUMN_AGG, data.getDataString(1));
            source.put(NodeRefTable.COLUMN_TIME_BUCKET, data.getDataLong(0));

            IndexRequestBuilder builder = getClient().prepareIndex(NodeRefTable.TABLE, id).setSource();
            indexRequestBuilders.add(builder);
        });
        return indexRequestBuilders;
    }
}
