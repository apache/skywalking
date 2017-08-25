package org.skywalking.apm.collector.agentstream.worker.noderef.summary.define;

import org.skywalking.apm.collector.storage.define.noderef.NodeRefSumTable;
import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;

/**
 * @author pengys5
 */
public class NodeRefSumH2TableDefine extends H2TableDefine {

    public NodeRefSumH2TableDefine() {
        super(NodeRefSumTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_FRONT_APPLICATION_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_BEHIND_APPLICATION_ID, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_BEHIND_PEER, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_S1_LTE, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_S3_LTE, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_S5_LTE, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_S5_GT, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_SUMMARY, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_ERROR, H2ColumnDefine.Type.Int.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
