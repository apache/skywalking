package org.skywalking.apm.collector.agentstream.worker.noderef.summary.define;

import org.skywalking.apm.collector.storage.h2.define.H2ColumnDefine;
import org.skywalking.apm.collector.storage.h2.define.H2TableDefine;
import org.skywalking.apm.collector.storage.define.noderef.NodeRefSumTable;

/**
 * @author pengys5
 */
public class NodeRefSumH2TableDefine extends H2TableDefine {

    public NodeRefSumH2TableDefine() {
        super(NodeRefSumTable.TABLE);
    }

    @Override public void initialize() {
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_ONE_SECOND_LESS, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_THREE_SECOND_LESS, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_FIVE_SECOND_LESS, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_FIVE_SECOND_GREATER, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_ERROR, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_SUMMARY, H2ColumnDefine.Type.Bigint.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_AGG, H2ColumnDefine.Type.Varchar.name()));
        addColumn(new H2ColumnDefine(NodeRefSumTable.COLUMN_TIME_BUCKET, H2ColumnDefine.Type.Bigint.name()));
    }
}
