package org.skywalking.apm.collector.storage.define.node;

import org.skywalking.apm.collector.storage.define.CommonTable;

/**
 * @author pengys5
 */
public class NodeComponentTable extends CommonTable {
    public static final String TABLE = "node_component";
    public static final String COLUMN_COMPONENT_ID = "component_id";
    public static final String COLUMN_COMPONENT_NAME = "component_name";
    public static final String COLUMN_PEER = "peer";
    public static final String COLUMN_PEER_ID = "peer_id";
}
