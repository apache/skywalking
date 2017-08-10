package org.skywalking.apm.collector.agentjvm.worker.memory.define;

import org.skywalking.apm.collector.stream.worker.storage.CommonTable;

/**
 * @author pengys5
 */
public class MemoryMetricTable extends CommonTable {
    public static final String TABLE = "memory_metric";
    public static final String COLUMN_APPLICATION_INSTANCE_ID = "application_instance_id";
    public static final String COLUMN_IS_HEAP = "is_heap";
    public static final String COLUMN_INIT = "init";
    public static final String COLUMN_MAX = "max";
    public static final String COLUMN_USED = "used";
    public static final String COLUMN_COMMITTED = "committed";
}
