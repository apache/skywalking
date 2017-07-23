package org.skywalking.apm.collector.agentstream.worker.node.define;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;

/**
 * @author pengys5
 */
public class NodeMappingH2TableDefine extends H2TableDefine {

    public NodeMappingH2TableDefine() {
        super(NodeMappingTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(NodeMappingTable.COLUMN_NAME, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(NodeMappingTable.COLUMN_PEERS, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(NodeMappingTable.COLUMN_AGG, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(NodeMappingTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
