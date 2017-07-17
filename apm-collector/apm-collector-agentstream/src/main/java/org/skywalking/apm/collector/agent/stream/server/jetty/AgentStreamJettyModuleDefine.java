package org.skywalking.apm.collector.agent.stream.server.jetty;

import org.skywalking.apm.collector.core.agentstream.AgentStreamModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleGroup;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.server.jetty.JettyServer;

/**
 * @author pengys5
 */
public class AgentStreamJettyModuleDefine extends AgentStreamModuleDefine {

    @Override protected ModuleGroup group() {
        return ModuleGroup.AgentStream;
    }

    @Override public String name() {
        return "jetty";
    }

    @Override public boolean defaultModule() {
        return false;
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
}
