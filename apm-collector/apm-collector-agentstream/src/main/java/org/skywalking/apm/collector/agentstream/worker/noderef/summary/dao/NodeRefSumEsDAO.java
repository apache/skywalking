package org.skywalking.apm.collector.agentstream.worker.noderef.summary.dao;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.skywalking.apm.collector.agentstream.worker.noderef.summary.define.NodeRefSumTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.data.Data;

/**
 * @author pengys5
 */
public class NodeRefSumEsDAO extends EsDAO implements INodeRefSumDAO {

    @Override public List<?> prepareBatch(Map<String, Data> dataMap) {
        List<IndexRequestBuilder> indexRequestBuilders = new ArrayList<>();
        dataMap.forEach((id, data) -> {
            Map<String, Object> source = new HashMap();
            source.put(NodeRefSumTable.COLUMN_ONE_SECOND_LESS, data.getDataLong(0));
            source.put(NodeRefSumTable.COLUMN_THREE_SECOND_LESS, data.getDataLong(1));
            source.put(NodeRefSumTable.COLUMN_FIVE_SECOND_LESS, data.getDataLong(2));
            source.put(NodeRefSumTable.COLUMN_FIVE_SECOND_GREATER, data.getDataLong(3));
            source.put(NodeRefSumTable.COLUMN_ERROR, data.getDataLong(4));
            source.put(NodeRefSumTable.COLUMN_SUMMARY, data.getDataLong(5));
            source.put(NodeRefSumTable.COLUMN_AGG, data.getDataString(1));
            source.put(NodeRefSumTable.COLUMN_TIME_BUCKET, data.getDataLong(6));

            IndexRequestBuilder builder = getClient().prepareIndex(NodeRefSumTable.TABLE, id).setSource(source);
            indexRequestBuilders.add(builder);
        });
        return indexRequestBuilders;
    }
}
