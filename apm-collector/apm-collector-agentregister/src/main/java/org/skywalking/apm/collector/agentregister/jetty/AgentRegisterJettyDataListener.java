package org.skywalking.apm.collector.agentregister.jetty;

import org.skywalking.apm.collector.agentregister.AgentRegisterModuleGroupDefine;
import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;

/**
 * @author pengys5
 */
public class AgentRegisterJettyDataListener extends ClusterDataListener {

    public static final String PATH = ClusterModuleDefine.BASE_CATALOG + "." + AgentRegisterModuleGroupDefine.GROUP_NAME + "." + AgentRegisterJettyModuleDefine.MODULE_NAME;

    @Override public String path() {
        return PATH;
    }

    @Override public void addressChangedNotify() {
    }
}
