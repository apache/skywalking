package org.skywalking.apm.collector.storage.define.register;

import org.skywalking.apm.collector.storage.define.CommonTable;

/**
 * @author pengys5
 */
public class ServiceNameTable extends CommonTable {
    public static final String TABLE = "service_name";
    public static final String COLUMN_SERVICE_NAME = "service_name";
    public static final String COLUMN_APPLICATION_ID = "application_id";
    public static final String COLUMN_SERVICE_ID = "service_id";
}
