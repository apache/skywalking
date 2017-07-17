package org.skywalking.apm.collector.agent.stream.server.grpc;

import org.skywalking.apm.collector.core.agentstream.AgentStreamModuleDefine;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleGroup;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.server.grpc.GRPCServer;

/**
 * @author pengys5
 */
public class AgentStreamGRPCModuleDefine extends AgentStreamModuleDefine {

    @Override protected ModuleGroup group() {
        return ModuleGroup.AgentStream;
    }

    @Override public String name() {
        return "grpc";
    }

    @Override public boolean defaultModule() {
        return true;
    }

    @Override protected ModuleConfigParser configParser() {
        return new AgentStreamGRPCConfigParser();
    }

    @Override protected Server server() {
        return new GRPCServer(AgentStreamGRPCConfig.HOST, AgentStreamGRPCConfig.PORT);
    }

    @Override protected ModuleRegistration registration() {
        return new AgentStreamGRPCModuleRegistration();
    }
}
