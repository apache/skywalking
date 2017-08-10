package org.skywalking.apm.collector.agentstream.worker.serviceref.reference.define;

import org.skywalking.apm.collector.stream.worker.storage.CommonTable;

/**
 * @author pengys5
 */
public class ServiceRefTable extends CommonTable {
    public static final String TABLE = "service_reference";
    public static final String COLUMN_ENTRY_SERVICE = "entry_service";
}
