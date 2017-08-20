package org.skywalking.apm.collector.agentstream.worker.node.component.define;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;
import org.skywalking.apm.collector.storage.define.node.NodeComponentTable;

/**
 * @author pengys5
 */
public class NodeComponentH2TableDefine extends H2TableDefine {

    public NodeComponentH2TableDefine() {
        super(NodeComponentTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(NodeComponentTable.COLUMN_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(NodeComponentTable.COLUMN_AGG, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(NodeComponentTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
