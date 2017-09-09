package org.skywalking.apm.collector.storage.define.jvm;

import org.skywalking.apm.collector.storage.define.CommonTable;

/**
 * @author pengys5
 */
public class GCMetricTable extends CommonTable {
    public static final String TABLE = "gc_metric";
    public static final String COLUMN_INSTANCE_ID = "instance_id";
    public static final String COLUMN_PHRASE = "phrase";
    public static final String COLUMN_COUNT = "count";
    public static final String COLUMN_TIME = "time";
}
