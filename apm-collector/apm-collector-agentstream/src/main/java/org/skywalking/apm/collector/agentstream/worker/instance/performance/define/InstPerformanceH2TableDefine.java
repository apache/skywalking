package org.skywalking.apm.collector.agentstream.worker.instance.performance.define;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;
import org.skywalking.apm.collector.storage.table.instance.InstPerformanceTable;

/**
 * @author pengys5
 */
public class InstPerformanceH2TableDefine extends H2TableDefine {

    public InstPerformanceH2TableDefine() {
        super(InstPerformanceTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(InstPerformanceTable.COLUMN_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(InstPerformanceTable.COLUMN_APPLICATION_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(InstPerformanceTable.COLUMN_INSTANCE_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(InstPerformanceTable.COLUMN_CALL_TIMES, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(InstPerformanceTable.COLUMN_COST_TOTAL, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(InstPerformanceTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
