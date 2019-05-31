package org.apache.skywalking.apm.plugin.seata;

import org.apache.skywalking.apm.agent.core.context.tag.StringTag;

public class Constants {
    public static final StringTag XID = new StringTag("XID");
    public static final StringTag TRANSACTION_ID = new StringTag("TransactionID");
    public static final StringTag BRANCH_ID = new StringTag("BranchId");
    public static final StringTag RESOURCE_ID = new StringTag("ResourceId");
    public static final StringTag LOG_OPERATION = new StringTag("LogOperation");
}
