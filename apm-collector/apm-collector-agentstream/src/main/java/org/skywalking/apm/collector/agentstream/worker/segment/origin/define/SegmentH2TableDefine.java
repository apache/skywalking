package org.skywalking.apm.collector.agentstream.worker.segment.origin.define;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;
import org.skywalking.apm.collector.storage.table.segment.SegmentTable;

/**
 * @author pengys5
 */
public class SegmentH2TableDefine extends H2TableDefine {

    public SegmentH2TableDefine() {
        super(SegmentTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(SegmentTable.COLUMN_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(SegmentTable.COLUMN_DATA_BINARY, H2ColumnDefine.Type.BINARY.name()));
    }
}
