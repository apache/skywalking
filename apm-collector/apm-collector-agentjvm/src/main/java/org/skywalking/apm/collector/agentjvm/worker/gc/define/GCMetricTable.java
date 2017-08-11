package org.skywalking.apm.collector.agentjvm.worker.gc.define;

import org.skywalking.apm.collector.stream.worker.storage.CommonTable;

/**
 * @author pengys5
 */
public class GCMetricTable extends CommonTable {
    public static final String TABLE = "gc_metric";
    public static final String COLUMN_APPLICATION_INSTANCE_ID = "application_instance_id";
    public static final String COLUMN_PHRASE = "phrase";
    public static final String COLUMN_COUNT = "count";
    public static final String COLUMN_TIME = "time";
}
