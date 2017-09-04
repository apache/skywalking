package org.skywalking.apm.collector.agentserver.jetty;

import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public class AgentServerJettyModuleRegistration extends ModuleRegistration {

    @Override public Value buildValue() {
        return new Value(AgentServerJettyConfig.HOST, AgentServerJettyConfig.PORT, AgentServerJettyConfig.CONTEXT_PATH);
    }
}
