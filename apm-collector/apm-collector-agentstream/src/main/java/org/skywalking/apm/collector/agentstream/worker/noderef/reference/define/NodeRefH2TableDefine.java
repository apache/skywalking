package org.skywalking.apm.collector.agentstream.worker.noderef.reference.define;

import org.skywalking.apm.collector.storage.define.noderef.NodeRefTable;
import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;

/**
 * @author pengys5
 */
public class NodeRefH2TableDefine extends H2TableDefine {

    public NodeRefH2TableDefine() {
        super(NodeRefTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(NodeRefTable.COLUMN_FRONT_APPLICATION_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefTable.COLUMN_BEHIND_APPLICATION_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefTable.COLUMN_BEHIND_PEER, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(NodeRefTable.COLUMN_S1_LTE, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefTable.COLUMN_S3_LTE, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefTable.COLUMN_S5_LTE, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefTable.COLUMN_S5_GT, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefTable.COLUMN_SUMMARY, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefTable.COLUMN_ERROR, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
