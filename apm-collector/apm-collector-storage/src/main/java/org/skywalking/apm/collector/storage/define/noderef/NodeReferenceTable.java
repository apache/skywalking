package org.skywalking.apm.collector.storage.define.noderef;

import org.skywalking.apm.collector.storage.define.CommonTable;

/**
 * @author pengys5
 */
public class NodeReferenceTable extends CommonTable {
    public static final String TABLE = "node_reference";
    public static final String COLUMN_FRONT_APPLICATION_ID = "front_application_id";
    public static final String COLUMN_BEHIND_APPLICATION_ID = "behind_application_id";
    public static final String COLUMN_BEHIND_PEER = "behind_peer";
    public static final String COLUMN_S1_LTE = "s1_lte";
    public static final String COLUMN_S3_LTE = "s3_lte";
    public static final String COLUMN_S5_LTE = "s5_lte";
    public static final String COLUMN_S5_GT = "s5_gt";
    public static final String COLUMN_SUMMARY = "summary";
    public static final String COLUMN_ERROR = "error";
}
