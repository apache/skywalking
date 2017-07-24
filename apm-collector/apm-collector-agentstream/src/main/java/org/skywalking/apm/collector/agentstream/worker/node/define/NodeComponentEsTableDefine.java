package org.skywalking.apm.collector.agentstream.worker.node.define;

import org.skywalking.apm.collector.agentstream.worker.node.component.NodeComponentTable;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;

/**
 * @author pengys5
 */
public class NodeComponentEsTableDefine extends ElasticSearchTableDefine {

    public NodeComponentEsTableDefine() {
        super(NodeComponentTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 0;
    }

    @Override public int numberOfShards() {
        return 2;
    }

    @Override public int numberOfReplicas() {
        return 0;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(NodeComponentTable.COLUMN_NAME, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(NodeComponentTable.COLUMN_PEERS, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(NodeComponentTable.COLUMN_AGG, ElasticSearchColumnDefine.Type.Keyword.name()));
    }
}
