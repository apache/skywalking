package org.skywalking.apm.collector.agentstream.worker.serviceref.reference.define;

import org.skywalking.apm.collector.storage.define.serviceref.ServiceRefTable;
import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;

/**
 * @author pengys5
 */
public class ServiceRefH2TableDefine extends H2TableDefine {

    public ServiceRefH2TableDefine() {
        super(ServiceRefTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(ServiceRefTable.COLUMN_AGG, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(ServiceRefTable.COLUMN_ENTRY_SERVICE, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(ServiceRefTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
