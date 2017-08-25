package org.skywalking.apm.collector.storage.define.node;

import org.skywalking.apm.collector.storage.define.CommonTable;

/**
 * @author pengys5
 */
public class NodeMappingTable extends CommonTable {
    public static final String TABLE = "node_mapping";
    public static final String COLUMN_APPLICATION_ID = "application_id";
    public static final String COLUMN_ADDRESS_ID = "address_id";
    public static final String COLUMN_ADDRESS = "address";
}
