package org.skywalking.apm.collector.storage.define.instance;

import org.skywalking.apm.collector.storage.define.CommonTable;

/**
 * @author pengys5
 */
public class InstPerformanceTable extends CommonTable {
    public static final String TABLE = "instance_performance";
    public static final String COLUMN_APPLICATION_ID = "application_id";
    public static final String COLUMN_INSTANCE_ID = "instance_id";
    public static final String COLUMN_CALLS = "calls";
    public static final String COLUMN_COST_TOTAL = "cost_total";
}
