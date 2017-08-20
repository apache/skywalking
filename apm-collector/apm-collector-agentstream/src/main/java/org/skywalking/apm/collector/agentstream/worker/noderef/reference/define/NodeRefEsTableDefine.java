package org.skywalking.apm.collector.agentstream.worker.noderef.reference.define;

import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;
import org.skywalking.apm.collector.storage.define.noderef.NodeRefTable;

/**
 * @author pengys5
 */
public class NodeRefEsTableDefine extends ElasticSearchTableDefine {

    public NodeRefEsTableDefine() {
        super(NodeRefTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 2;
    }

    @Override public int numberOfShards() {
        return 2;
    }

    @Override public int numberOfReplicas() {
        return 0;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(NodeRefTable.COLUMN_AGG, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(NodeRefTable.COLUMN_TIME_BUCKET, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
