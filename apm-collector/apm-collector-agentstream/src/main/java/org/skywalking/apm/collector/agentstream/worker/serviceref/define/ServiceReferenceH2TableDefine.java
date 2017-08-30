package org.skywalking.apm.collector.agentstream.worker.serviceref.define;

import org.skywalking.apm.collector.storage.define.serviceref.ServiceReferenceTable;
import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;

/**
 * @author pengys5
 */
public class ServiceReferenceH2TableDefine extends H2TableDefine {

    public ServiceReferenceH2TableDefine() {
        super(ServiceReferenceTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_ENTRY_SERVICE_NAME, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_FRONT_SERVICE_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_FRONT_SERVICE_NAME, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_BEHIND_SERVICE_NAME, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_S1_LTE, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_S3_LTE, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_S5_LTE, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_S5_GT, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_SUMMARY, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_ERROR, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_COST_SUMMARY, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(ServiceReferenceTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
