package org.skywalking.apm.collector.agentstream.jetty;

import org.skywalking.apm.collector.agentstream.AgentStreamModuleGroupDefine;
import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;

/**
 * @author pengys5
 */
public class AgentStreamJettyDataListener extends ClusterDataListener {

    public AgentStreamJettyDataListener(String moduleName) {
        super(moduleName);
    }

    @Override public String path() {
        return ClusterModuleDefine.BASE_CATALOG + "." + AgentStreamModuleGroupDefine.GROUP_NAME + "." + moduleName();
    }
}
