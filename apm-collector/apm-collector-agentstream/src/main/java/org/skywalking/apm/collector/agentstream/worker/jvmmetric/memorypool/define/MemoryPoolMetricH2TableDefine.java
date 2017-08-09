package org.skywalking.apm.collector.agentstream.worker.jvmmetric.memorypool.define;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;

/**
 * @author pengys5
 */
public class MemoryPoolMetricH2TableDefine extends H2TableDefine {

    public MemoryPoolMetricH2TableDefine() {
        super(MemoryPoolMetricTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(MemoryPoolMetricTable.COLUMN_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(MemoryPoolMetricTable.COLUMN_APPLICATION_INSTANCE_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(MemoryPoolMetricTable.COLUMN_POOL_TYPE, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(MemoryPoolMetricTable.COLUMN_IS_HEAP, H2ColumnDefine.Type.Boolean.name()));
        addColumn(new H2ColumnDefine(MemoryPoolMetricTable.COLUMN_INIT, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(MemoryPoolMetricTable.COLUMN_MAX, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(MemoryPoolMetricTable.COLUMN_USED, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(MemoryPoolMetricTable.COLUMN_COMMITTED, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(MemoryPoolMetricTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
