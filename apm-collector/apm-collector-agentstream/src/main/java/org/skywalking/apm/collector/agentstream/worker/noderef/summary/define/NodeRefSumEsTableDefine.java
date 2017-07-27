package org.skywalking.apm.collector.agentstream.worker.noderef.summary.define;

import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;

/**
 * @author pengys5
 */
public class NodeRefSumEsTableDefine extends ElasticSearchTableDefine {

    public NodeRefSumEsTableDefine() {
        super(NodeRefSumTable.TABLE);
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
        addColumn(new ElasticSearchColumnDefine(NodeRefSumTable.COLUMN_ONE_SECOND_LESS, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(NodeRefSumTable.COLUMN_THREE_SECOND_LESS, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(NodeRefSumTable.COLUMN_FIVE_SECOND_LESS, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(NodeRefSumTable.COLUMN_FIVE_SECOND_GREATER, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(NodeRefSumTable.COLUMN_ERROR, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(NodeRefSumTable.COLUMN_SUMMARY, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(NodeRefSumTable.COLUMN_AGG, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(NodeRefSumTable.COLUMN_TIME_BUCKET, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
