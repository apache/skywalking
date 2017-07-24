package org.skywalking.apm.collector.agentstream.worker.node.define;

import org.skywalking.apm.collector.agentstream.worker.node.component.NodeComponentTable;
import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;

/**
 * @author pengys5
 */
public class NodeComponentH2TableDefine extends H2TableDefine {

    public NodeComponentH2TableDefine() {
        super(NodeComponentTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(NodeComponentTable.COLUMN_NAME, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(NodeComponentTable.COLUMN_PEERS, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(NodeComponentTable.COLUMN_AGG, H2ColumnDefine.Type.Varchar.name()));
    }
}
