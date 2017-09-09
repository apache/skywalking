package org.skywalking.apm.collector.storage.define.jvm;

import org.skywalking.apm.collector.storage.define.CommonTable;

/**
 * @author pengys5
 */
public class CpuMetricTable extends CommonTable {
    public static final String TABLE = "cpu_metric";
    public static final String COLUMN_INSTANCE_ID = "instance_id";
    public static final String COLUMN_USAGE_PERCENT = "usage_percent";
}
