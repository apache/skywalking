package org.skywalking.apm.collector.agentstream.worker.service.entry.define;

import org.skywalking.apm.collector.storage.table.CommonTable;

/**
 * @author pengys5
 */
public class ServiceEntryTable extends CommonTable {
    public static final String TABLE = "service_entry";
    public static final String COLUMN_APPLICATION_ID = "application_id";
}
