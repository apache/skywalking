package org.skywalking.apm.collector.storage.define.jvm;

import org.skywalking.apm.collector.storage.define.CommonTable;

/**
 * @author pengys5
 */
public class MemoryPoolMetricTable extends CommonTable {
    public static final String TABLE = "memory_pool_metric";
    public static final String COLUMN_INSTANCE_ID = "instance_id";
    public static final String COLUMN_POOL_TYPE = "pool_type";
    public static final String COLUMN_IS_HEAP = "is_heap";
    public static final String COLUMN_INIT = "init";
    public static final String COLUMN_MAX = "max";
    public static final String COLUMN_USED = "used";
    public static final String COLUMN_COMMITTED = "committed";
}
