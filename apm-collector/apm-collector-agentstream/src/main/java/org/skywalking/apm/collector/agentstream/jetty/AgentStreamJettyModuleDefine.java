package org.skywalking.apm.collector.agentstream.jetty;

import org.skywalking.apm.collector.agentstream.AgentStreamModuleDefine;
import org.skywalking.apm.collector.agentstream.AgentStreamModuleGroupDefine;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.server.jetty.JettyServer;

/**
 * @author pengys5
 */
public class AgentStreamJettyModuleDefine extends AgentStreamModuleDefine {

    public static final String MODULE_NAME = "jetty";

    @Override protected String group() {
        return AgentStreamModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override protected ModuleConfigParser configParser() {
        return new AgentStreamJettyConfigParser();
    }

    @Override protected Server server() {
        return new JettyServer(AgentStreamJettyConfig.HOST, AgentStreamJettyConfig.PORT, AgentStreamJettyConfig.CONTEXT_PATH);
    }

    @Override protected ModuleRegistration registration() {
        return new AgentStreamJettyModuleRegistration();
    }

    @Override public ClusterDataListener listener() {
        return new AgentStreamJettyDataListener();
    }
}
