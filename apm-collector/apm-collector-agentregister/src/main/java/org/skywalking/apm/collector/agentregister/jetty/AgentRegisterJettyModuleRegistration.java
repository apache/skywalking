package org.skywalking.apm.collector.agentregister.jetty;

import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public class AgentRegisterJettyModuleRegistration extends ModuleRegistration {

    @Override public Value buildValue() {
        return new Value(AgentRegisterJettyConfig.HOST, AgentRegisterJettyConfig.PORT, AgentRegisterJettyConfig.CONTEXT_PATH);
    }
}
