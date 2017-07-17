package org.skywalking.apm.collector.agent.stream.server.grpc;

import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public class AgentStreamGRPCModuleRegistration extends ModuleRegistration {

    @Override public Value buildValue() {
        return new Value(AgentStreamGRPCConfig.HOST, AgentStreamGRPCConfig.PORT, null);
    }
}
