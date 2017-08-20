package org.skywalking.apm.collector.agentstream.worker.noderef.reference.define;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;
import org.skywalking.apm.collector.storage.define.noderef.NodeRefTable;

/**
 * @author pengys5
 */
public class NodeRefH2TableDefine extends H2TableDefine {

    public NodeRefH2TableDefine() {
        super(NodeRefTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(NodeRefTable.COLUMN_AGG, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(NodeRefTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
