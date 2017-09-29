package org.skywalking.apm.collector.storage.define.segment;

import org.skywalking.apm.collector.storage.define.CommonTable;

/**
 * @author pengys5
 */
public class SegmentCostTable extends CommonTable {
    public static final String TABLE = "segment_cost";
    public static final String COLUMN_SEGMENT_ID = "segment_id";
    public static final String COLUMN_APPLICATION_ID = "application_id";
    public static final String COLUMN_START_TIME = "start_time";
    public static final String COLUMN_END_TIME = "end_time";
    public static final String COLUMN_SERVICE_NAME = "service_name";
    public static final String COLUMN_COST = "cost";
    public static final String COLUMN_IS_ERROR = "is_error";
}
