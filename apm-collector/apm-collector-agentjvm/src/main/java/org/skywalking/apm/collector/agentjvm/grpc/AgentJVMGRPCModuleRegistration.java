package org.skywalking.apm.collector.agentjvm.grpc;

import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public class AgentJVMGRPCModuleRegistration extends ModuleRegistration {

    @Override public Value buildValue() {
        return new Value(AgentJVMGRPCConfig.HOST, AgentJVMGRPCConfig.PORT, null);
    }
}
