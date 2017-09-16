package org.skywalking.apm.collector.agentstream.worker.segment.cost.define;

import org.skywalking.apm.collector.storage.define.segment.SegmentCostTable;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchColumnDefine;
import org.skywalking.apm.collector.storage.elasticsearch.define.ElasticSearchTableDefine;

/**
 * @author pengys5
 */
public class SegmentCostEsTableDefine extends ElasticSearchTableDefine {

    public SegmentCostEsTableDefine() {
        super(SegmentCostTable.TABLE);
    }

    @Override public int refreshInterval() {
        return 5;
    }

    @Override public void initialize() {
        addColumn(new ElasticSearchColumnDefine(SegmentCostTable.COLUMN_SEGMENT_ID, ElasticSearchColumnDefine.Type.Keyword.name()));
        addColumn(new ElasticSearchColumnDefine(SegmentCostTable.COLUMN_SERVICE_NAME, ElasticSearchColumnDefine.Type.Text.name()));
        addColumn(new ElasticSearchColumnDefine(SegmentCostTable.COLUMN_COST, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(SegmentCostTable.COLUMN_START_TIME, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(SegmentCostTable.COLUMN_END_TIME, ElasticSearchColumnDefine.Type.Long.name()));
        addColumn(new ElasticSearchColumnDefine(SegmentCostTable.COLUMN_IS_ERROR, ElasticSearchColumnDefine.Type.Boolean.name()));
        addColumn(new ElasticSearchColumnDefine(SegmentCostTable.COLUMN_TIME_BUCKET, ElasticSearchColumnDefine.Type.Long.name()));
    }
}
