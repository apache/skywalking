package org.skywalking.apm.collector.agentstream.worker.segment.origin.define;

import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;
import org.skywalking.apm.collector.storage.define.segment.SegmentTable;

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

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(SegmentTable.COLUMN_DATA_BINARY, ElasticSearchColumnDefine.Type.Binary.name()));
    }
}
