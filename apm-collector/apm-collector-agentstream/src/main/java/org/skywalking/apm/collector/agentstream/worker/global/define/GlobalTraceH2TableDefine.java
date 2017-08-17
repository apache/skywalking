package org.skywalking.apm.collector.agentstream.worker.global.define;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;
import org.skywalking.apm.collector.storage.table.global.GlobalTraceTable;

/**
 * @author pengys5
 */
public class GlobalTraceH2TableDefine extends H2TableDefine {

    public GlobalTraceH2TableDefine() {
        super(GlobalTraceTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(GlobalTraceTable.COLUMN_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(GlobalTraceTable.COLUMN_SEGMENT_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(GlobalTraceTable.COLUMN_GLOBAL_TRACE_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(GlobalTraceTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
