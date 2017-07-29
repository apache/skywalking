package org.skywalking.apm.collector.agentstream.worker.node.mapping.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.skywalking.apm.collector.agentstream.worker.node.mapping.define.NodeMappingTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;

/**
 * @author pengys5
 */
public class NodeMappingEsDAO extends EsDAO implements INodeMappingDAO {

    @Override public List<?> prepareBatch(Map<String, Data> dataMap) {
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        dataMap.forEach((id, data) -> {
            Map<String, Object> source = new HashMap();
            source.put(NodeMappingTable.COLUMN_AGG, data.getDataString(1));
            source.put(NodeMappingTable.COLUMN_TIME_BUCKET, data.getDataLong(0));

            IndexRequestBuilder builder = getClient().prepareIndex(NodeMappingTable.TABLE, id).setSource();
            indexRequestBuilders.add(builder);
        });
        return indexRequestBuilders;
    }
}
