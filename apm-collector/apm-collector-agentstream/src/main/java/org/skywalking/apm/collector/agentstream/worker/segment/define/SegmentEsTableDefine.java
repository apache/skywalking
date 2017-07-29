package org.skywalking.apm.collector.agentstream.worker.segment.define;

import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;

/**
 * @author pengys5
 */
public class SegmentEsTableDefine extends ElasticSearchTableDefine {

    public SegmentEsTableDefine() {
        super(SegmentTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 10;
    }

    @Override public int numberOfShards() {
        return 2;
    }

    @Override public int numberOfReplicas() {
        return 0;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(SegmentTable.COLUMN_DATA_BINARY, ElasticSearchColumnDefine.Type.Binary.name()));
    }
}
