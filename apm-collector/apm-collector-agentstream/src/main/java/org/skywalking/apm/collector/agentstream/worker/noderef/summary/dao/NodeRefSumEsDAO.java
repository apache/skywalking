package org.skywalking.apm.collector.agentstream.worker.noderef.summary.dao;

import java.util.HashMap;
import java.util.Map;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.index.IndexRequestBuilder;
import org.elasticsearch.action.update.UpdateRequestBuilder;
import org.skywalking.apm.collector.core.stream.Data;
import org.skywalking.apm.collector.storage.define.DataDefine;
import org.skywalking.apm.collector.storage.define.noderef.NodeRefSumTable;
import org.skywalking.apm.collector.storage.elasticsearch.dao.EsDAO;
import org.skywalking.apm.collector.stream.worker.impl.dao.IPersistenceDAO;

/**
 * @author pengys5
 */
public class NodeRefSumEsDAO extends EsDAO implements INodeRefSumDAO, IPersistenceDAO<IndexRequestBuilder, UpdateRequestBuilder> {

    @Override public Data get(String id, DataDefine dataDefine) {
        GetResponse getResponse = getClient().prepareGet(NodeRefSumTable.TABLE, id).get();
        if (getResponse.isExists()) {
            Data data = dataDefine.build(id);
            Map<String, Object> source = getResponse.getSource();
            data.setDataInteger(0, ((Number)source.get(NodeRefSumTable.COLUMN_FRONT_APPLICATION_ID)).intValue());
            data.setDataInteger(1, ((Number)source.get(NodeRefSumTable.COLUMN_BEHIND_APPLICATION_ID)).intValue());
            data.setDataString(1, (String)source.get(NodeRefSumTable.COLUMN_BEHIND_PEER));
            data.setDataInteger(2, ((Number)source.get(NodeRefSumTable.COLUMN_S1_LTE)).intValue());
            data.setDataInteger(3, ((Number)source.get(NodeRefSumTable.COLUMN_S3_LTE)).intValue());
            data.setDataInteger(4, ((Number)source.get(NodeRefSumTable.COLUMN_S5_LTE)).intValue());
            data.setDataInteger(5, ((Number)source.get(NodeRefSumTable.COLUMN_S5_GT)).intValue());
            data.setDataInteger(6, ((Number)source.get(NodeRefSumTable.COLUMN_SUMMARY)).intValue());
            data.setDataInteger(7, ((Number)source.get(NodeRefSumTable.COLUMN_ERROR)).intValue());
            data.setDataLong(0, ((Number)source.get(NodeRefSumTable.COLUMN_TIME_BUCKET)).longValue());
            return data;
        } else {
            return null;
        }
    }

    @Override public IndexRequestBuilder prepareBatchInsert(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(NodeRefSumTable.COLUMN_FRONT_APPLICATION_ID, data.getDataInteger(0));
        source.put(NodeRefSumTable.COLUMN_BEHIND_APPLICATION_ID, data.getDataInteger(1));
        source.put(NodeRefSumTable.COLUMN_BEHIND_PEER, data.getDataString(1));
        source.put(NodeRefSumTable.COLUMN_S1_LTE, data.getDataInteger(2));
        source.put(NodeRefSumTable.COLUMN_S3_LTE, data.getDataInteger(3));
        source.put(NodeRefSumTable.COLUMN_S5_LTE, data.getDataInteger(4));
        source.put(NodeRefSumTable.COLUMN_S5_GT, data.getDataInteger(5));
        source.put(NodeRefSumTable.COLUMN_SUMMARY, data.getDataInteger(6));
        source.put(NodeRefSumTable.COLUMN_ERROR, data.getDataInteger(7));
        source.put(NodeRefSumTable.COLUMN_TIME_BUCKET, data.getDataLong(0));

        return getClient().prepareIndex(NodeRefSumTable.TABLE, data.getDataString(0)).setSource(source);
    }

    @Override public UpdateRequestBuilder prepareBatchUpdate(Data data) {
        Map<String, Object> source = new HashMap<>();
        source.put(NodeRefSumTable.COLUMN_FRONT_APPLICATION_ID, data.getDataInteger(0));
        source.put(NodeRefSumTable.COLUMN_BEHIND_APPLICATION_ID, data.getDataInteger(1));
        source.put(NodeRefSumTable.COLUMN_BEHIND_PEER, data.getDataString(1));
        source.put(NodeRefSumTable.COLUMN_S1_LTE, data.getDataInteger(2));
        source.put(NodeRefSumTable.COLUMN_S3_LTE, data.getDataInteger(3));
        source.put(NodeRefSumTable.COLUMN_S5_LTE, data.getDataInteger(4));
        source.put(NodeRefSumTable.COLUMN_S5_GT, data.getDataInteger(5));
        source.put(NodeRefSumTable.COLUMN_SUMMARY, data.getDataInteger(6));
        source.put(NodeRefSumTable.COLUMN_ERROR, data.getDataInteger(7));
        source.put(NodeRefSumTable.COLUMN_TIME_BUCKET, data.getDataLong(0));

        return getClient().prepareUpdate(NodeRefSumTable.TABLE, data.getDataString(0)).setDoc(source);
    }
}
