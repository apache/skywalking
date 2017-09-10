package org.skywalking.apm.collector.agentstream.grpc;

import org.skywalking.apm.collector.agentstream.AgentStreamModuleGroupDefine;
import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;

/**
 * @author pengys5
 */
public class AgentStreamGRPCDataListener extends ClusterDataListener {

    public static final String PATH = ClusterModuleDefine.BASE_CATALOG + "." + AgentStreamModuleGroupDefine.GROUP_NAME + "." + AgentStreamGRPCModuleDefine.MODULE_NAME;

    @Override public String path() {
        return PATH;
    }

    @Override public void serverJoinNotify(String serverAddress) {
        
    }

    @Override public void serverQuitNotify(String serverAddress) {

    }
}
