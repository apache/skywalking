package org.skywalking.apm.collector.storage.define.global;

import org.skywalking.apm.collector.storage.define.CommonTable;

/**
 * @author pengys5
 */
public class GlobalTraceTable extends CommonTable {
    public static final String TABLE = "global_trace";
    public static final String COLUMN_SEGMENT_ID = "segment_id";
    public static final String COLUMN_GLOBAL_TRACE_ID = "global_trace_id";
}
