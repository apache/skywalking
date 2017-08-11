package org.skywalking.apm.collector.agentjvm.worker.cpu.define;

import org.skywalking.apm.collector.stream.worker.storage.CommonTable;

/**
 * @author pengys5
 */
public class CpuMetricTable extends CommonTable {
    public static final String TABLE = "cpu_metric";
    public static final String COLUMN_APPLICATION_INSTANCE_ID = "application_instance_id";
    public static final String COLUMN_USAGE_PERCENT = "usage_percent";
}
