package org.skywalking.apm.collector.agentjvm.worker.gc.define;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;

/**
 * @author pengys5
 */
public class GCMetricH2TableDefine extends H2TableDefine {

    public GCMetricH2TableDefine() {
        super(GCMetricTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(GCMetricTable.COLUMN_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(GCMetricTable.COLUMN_APPLICATION_INSTANCE_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(GCMetricTable.COLUMN_PHRASE, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(GCMetricTable.COLUMN_COUNT, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(GCMetricTable.COLUMN_TIME, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(GCMetricTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
