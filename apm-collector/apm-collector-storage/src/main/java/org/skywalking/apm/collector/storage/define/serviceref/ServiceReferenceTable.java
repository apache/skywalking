package org.skywalking.apm.collector.storage.define.serviceref;

import org.skywalking.apm.collector.storage.define.CommonTable;

/**
 * @author pengys5
 */
public class ServiceReferenceTable extends CommonTable {
    public static final String TABLE = "service_reference";
    public static final String COLUMN_ENTRY_SERVICE_ID = "entry_service_id";
    public static final String COLUMN_ENTRY_SERVICE_NAME = "entry_service_name";
    public static final String COLUMN_FRONT_SERVICE_ID = "front_service_id";
    public static final String COLUMN_FRONT_SERVICE_NAME = "front_service_name";
    public static final String COLUMN_BEHIND_SERVICE_ID = "behind_service_id";
    public static final String COLUMN_BEHIND_SERVICE_NAME = "behind_service_name";
    public static final String COLUMN_S1_LTE = "s1_lte";
    public static final String COLUMN_S3_LTE = "s3_lte";
    public static final String COLUMN_S5_LTE = "s5_lte";
    public static final String COLUMN_S5_GT = "s5_gt";
    public static final String COLUMN_SUMMARY = "summary";
    public static final String COLUMN_ERROR = "error";
}
