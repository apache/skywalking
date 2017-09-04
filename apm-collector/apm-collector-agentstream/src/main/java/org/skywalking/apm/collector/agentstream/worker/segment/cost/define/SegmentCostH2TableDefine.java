package org.skywalking.apm.collector.agentstream.worker.segment.cost.define;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;
import org.skywalking.apm.collector.storage.define.segment.SegmentCostTable;

/**
 * @author pengys5
 */
public class SegmentCostH2TableDefine extends H2TableDefine {

    public SegmentCostH2TableDefine() {
        super(SegmentCostTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(SegmentCostTable.COLUMN_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(SegmentCostTable.COLUMN_SEGMENT_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(SegmentCostTable.COLUMN_SERVICE_NAME, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(SegmentCostTable.COLUMN_COST, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(SegmentCostTable.COLUMN_START_TIME, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(SegmentCostTable.COLUMN_END_TIME, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(SegmentCostTable.COLUMN_IS_ERROR, H2ColumnDefine.Type.Boolean.name()));
        addColumn(new H2ColumnDefine(SegmentCostTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
