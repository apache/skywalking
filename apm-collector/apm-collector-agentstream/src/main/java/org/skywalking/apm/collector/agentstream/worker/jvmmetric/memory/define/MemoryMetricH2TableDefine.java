package org.skywalking.apm.collector.agentstream.worker.jvmmetric.memory.define;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;

/**
 * @author pengys5
 */
public class MemoryMetricH2TableDefine extends H2TableDefine {

    public MemoryMetricH2TableDefine() {
        super(MemoryMetricTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_APPLICATION_INSTANCE_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_IS_HEAP, H2ColumnDefine.Type.Boolean.name()));
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_INIT, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_MAX, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_USED, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_COMMITTED, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(MemoryMetricTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
