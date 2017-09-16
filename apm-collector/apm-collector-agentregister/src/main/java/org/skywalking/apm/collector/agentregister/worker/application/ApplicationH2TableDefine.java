package org.skywalking.apm.collector.agentregister.worker.application;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;
import org.skywalking.apm.collector.storage.define.register.ApplicationTable;

/**
 * @author pengys5
 */
public class ApplicationH2TableDefine extends H2TableDefine {

    public ApplicationH2TableDefine() {
        super(ApplicationTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(ApplicationTable.COLUMN_APPLICATION_CODE, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(ApplicationTable.COLUMN_APPLICATION_ID, H2ColumnDefine.Type.Int.name()));
    }
}
