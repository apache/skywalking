package org.skywalking.apm.collector.agentstream.worker.noderef.define;

import org.skywalking.apm.collector.storage.define.noderef.NodeReferenceTable;
import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;

/**
 * @author pengys5
 */
public class NodeReferenceH2TableDefine extends H2TableDefine {

    public NodeReferenceH2TableDefine() {
        super(NodeReferenceTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(NodeReferenceTable.COLUMN_ID, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(NodeReferenceTable.COLUMN_FRONT_APPLICATION_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeReferenceTable.COLUMN_BEHIND_APPLICATION_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeReferenceTable.COLUMN_BEHIND_PEER, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(NodeReferenceTable.COLUMN_S1_LTE, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeReferenceTable.COLUMN_S3_LTE, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeReferenceTable.COLUMN_S5_LTE, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeReferenceTable.COLUMN_S5_GT, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeReferenceTable.COLUMN_SUMMARY, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeReferenceTable.COLUMN_ERROR, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeReferenceTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
