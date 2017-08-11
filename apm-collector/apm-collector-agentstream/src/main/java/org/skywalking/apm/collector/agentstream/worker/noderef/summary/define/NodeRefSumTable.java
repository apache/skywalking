package org.skywalking.apm.collector.agentstream.worker.noderef.summary.define;

import org.skywalking.apm.collector.stream.worker.storage.CommonTable;

/**
 * @author pengys5
 */
public class NodeRefSumTable extends CommonTable {
    public static final String TABLE = "node_reference_sum";
    public static final String COLUMN_ONE_SECOND_LESS = "one_second_less";
    public static final String COLUMN_THREE_SECOND_LESS = "three_second_less";
    public static final String COLUMN_FIVE_SECOND_LESS = "five_second_less";
    public static final String COLUMN_FIVE_SECOND_GREATER = "five_second_greater";
    public static final String COLUMN_ERROR = "error";
    public static final String COLUMN_SUMMARY = "summary";
}
