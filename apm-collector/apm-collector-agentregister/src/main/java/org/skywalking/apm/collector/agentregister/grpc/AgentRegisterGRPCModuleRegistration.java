package org.skywalking.apm.collector.agentregister.grpc;

import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public class AgentRegisterGRPCModuleRegistration extends ModuleRegistration {

    @Override public Value buildValue() {
        return new Value(AgentRegisterGRPCConfig.HOST, AgentRegisterGRPCConfig.PORT, null);
    }
}
