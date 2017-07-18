package org.skywalking.apm.collector.agentstream.jetty;

import com.google.gson.JsonObject;
import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public class AgentStreamJettyModuleRegistration extends ModuleRegistration {

    @Override public Value buildValue() {
        JsonObject data = new JsonObject();
        data.addProperty(AgentStreamJettyConfigParser.CONTEXT_PATH, AgentStreamJettyConfig.CONTEXT_PATH);
        return new Value(AgentStreamJettyConfig.HOST, AgentStreamJettyConfig.PORT, data);
    }
}
