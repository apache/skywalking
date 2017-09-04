package org.skywalking.apm.collector.storage.define.service;

import org.skywalking.apm.collector.storage.define.CommonTable;

/**
 * @author pengys5
 */
public class ServiceEntryTable extends CommonTable {
    public static final String TABLE = "service_entry";
    public static final String COLUMN_APPLICATION_ID = "application_id";
    public static final String COLUMN_ENTRY_SERVICE_ID = "entry_service_id";
    public static final String COLUMN_ENTRY_SERVICE_NAME = "entry_service_name";
    public static final String COLUMN_REGISTER_TIME = "register_time";
    public static final String COLUMN_NEWEST_TIME = "newest_time";
}
