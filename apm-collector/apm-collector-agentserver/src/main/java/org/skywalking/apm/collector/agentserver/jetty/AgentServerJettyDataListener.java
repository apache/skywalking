package org.skywalking.apm.collector.agentserver.jetty;

import org.skywalking.apm.collector.agentserver.AgentServerModuleGroupDefine;
import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;

/**
 * @author pengys5
 */
public class AgentServerJettyDataListener extends ClusterDataListener {

    @Override public String path() {
        return ClusterModuleDefine.BASE_CATALOG + "." + AgentServerModuleGroupDefine.GROUP_NAME + "." + AgentServerJettyModuleDefine.MODULE_NAME;
    }

    @Override public void serverJoinNotify(String serverAddress) {

    }

    @Override public void serverQuitNotify(String serverAddress) {

    }
}
