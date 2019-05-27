package org.apache.skywalking.apm.plugin.seata;

import org.apache.skywalking.apm.agent.core.context.tag.StringTag;

public class Constants {
  public static final StringTag XID = new StringTag("XID");
  public static final StringTag BRANCH_ID = new StringTag("BranchId");

}
