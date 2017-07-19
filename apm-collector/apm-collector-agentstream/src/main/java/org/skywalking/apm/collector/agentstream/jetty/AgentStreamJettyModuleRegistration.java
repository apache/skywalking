package org.skywalking.apm.collector.agentstream.jetty;

import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public class AgentStreamJettyModuleRegistration extends ModuleRegistration {

    @Override public Value buildValue() {
        return new Value(AgentStreamJettyConfig.HOST, AgentStreamJettyConfig.PORT, AgentStreamJettyConfig.CONTEXT_PATH);
    }
}
