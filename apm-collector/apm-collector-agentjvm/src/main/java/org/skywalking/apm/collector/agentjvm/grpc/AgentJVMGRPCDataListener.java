package org.skywalking.apm.collector.agentjvm.grpc;

import org.skywalking.apm.collector.agentjvm.AgentJVMModuleGroupDefine;
import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;

/**
 * @author pengys5
 */
public class AgentJVMGRPCDataListener extends ClusterDataListener {

    public static final String PATH = ClusterModuleDefine.BASE_CATALOG + "." + AgentJVMModuleGroupDefine.GROUP_NAME + "." + AgentJVMGRPCModuleDefine.MODULE_NAME;

    @Override public String path() {
        return PATH;
    }
}
