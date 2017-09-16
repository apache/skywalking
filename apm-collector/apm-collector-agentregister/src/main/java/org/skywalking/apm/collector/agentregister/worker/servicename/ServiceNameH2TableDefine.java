package org.skywalking.apm.collector.agentregister.worker.servicename;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;
import org.skywalking.apm.collector.storage.define.register.ServiceNameTable;

/**
 * @author pengys5
 */
public class ServiceNameH2TableDefine extends H2TableDefine {

    public ServiceNameH2TableDefine() {
        super(ServiceNameTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(ServiceNameTable.COLUMN_APPLICATION_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(ServiceNameTable.COLUMN_SERVICE_NAME, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(ServiceNameTable.COLUMN_SERVICE_ID, H2ColumnDefine.Type.Int.name()));
    }
}
