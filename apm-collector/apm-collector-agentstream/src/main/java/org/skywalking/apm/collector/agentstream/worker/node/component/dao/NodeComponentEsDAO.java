package org.skywalking.apm.collector.agentstream.worker.node.component.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.skywalking.apm.collector.agentstream.worker.node.component.define.NodeComponentTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;

/**
 * @author pengys5
 */
public class NodeComponentEsDAO extends EsDAO implements INodeComponentDAO {

    @Override public List<?> prepareBatch(Map<String, Data> dataMap) {
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        dataMap.forEach((id, data) -> {
            Map<String, Object> source = new HashMap();
            source.put(NodeComponentTable.COLUMN_AGG, data.getDataString(1));
            source.put(NodeComponentTable.COLUMN_TIME_BUCKET, data.getDataLong(0));

            IndexRequestBuilder builder = getClient().prepareIndex(NodeComponentTable.TABLE, id).setSource(source);
            indexRequestBuilders.add(builder);
        });
        return indexRequestBuilders;
    }
}
