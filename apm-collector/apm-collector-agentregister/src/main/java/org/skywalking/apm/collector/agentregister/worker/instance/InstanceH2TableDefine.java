package org.skywalking.apm.collector.agentregister.worker.instance;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;
import org.skywalking.apm.collector.storage.define.register.InstanceTable;

/**
 * @author pengys5
 */
public class InstanceH2TableDefine extends H2TableDefine {

    public InstanceH2TableDefine() {
        super(InstanceTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(InstanceTable.COLUMN_APPLICATION_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(InstanceTable.COLUMN_AGENT_UUID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(InstanceTable.COLUMN_REGISTER_TIME, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(InstanceTable.COLUMN_INSTANCE_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(InstanceTable.COLUMN_HEARTBEAT_TIME, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(InstanceTable.COLUMN_OS_INFO, H2ColumnDefine.Type.Varchar.name()));
    }
}
