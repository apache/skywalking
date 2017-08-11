package org.skywalking.apm.collector.agentstream.worker.register.instance;

import org.skywalking.apm.collector.stream.worker.storage.CommonTable;

/**
 * @author pengys5
 */
public class InstanceTable extends CommonTable {
    public static final String TABLE = "instance";
    public static final String COLUMN_APPLICATION_ID = "application_id";
    public static final String COLUMN_AGENT_UUID = "agent_uuid";
    public static final String COLUMN_REGISTER_TIME = "register_time";
    public static final String COLUMN_INSTANCE_ID = "instance_id";
    public static final String COLUMN_HEARTBEAT_TIME = "heartbeatTime";
}
