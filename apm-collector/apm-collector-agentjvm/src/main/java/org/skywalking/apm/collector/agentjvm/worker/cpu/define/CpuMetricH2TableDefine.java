package org.skywalking.apm.collector.agentjvm.worker.cpu.define;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;
import org.skywalking.apm.collector.storage.define.jvm.CpuMetricTable;

/**
 * @author pengys5
 */
public class CpuMetricH2TableDefine extends H2TableDefine {

    public CpuMetricH2TableDefine() {
        super(CpuMetricTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(CpuMetricTable.COLUMN_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(CpuMetricTable.COLUMN_APPLICATION_INSTANCE_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(CpuMetricTable.COLUMN_USAGE_PERCENT, H2ColumnDefine.Type.Double.name()));
        addColumn(new H2ColumnDefine(CpuMetricTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
