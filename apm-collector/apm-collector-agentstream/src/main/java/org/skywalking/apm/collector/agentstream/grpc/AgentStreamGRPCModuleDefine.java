package org.skywalking.apm.collector.agentstream.grpc;

import org.skywalking.apm.collector.agentstream.AgentStreamModuleDefine;
import org.skywalking.apm.collector.agentstream.AgentStreamModuleGroupDefine;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.server.grpc.GRPCServer;

/**
 * @author pengys5
 */
public class AgentStreamGRPCModuleDefine extends AgentStreamModuleDefine {

    @Override protected String group() {
        return AgentStreamModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return "grpc";
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

    @Override public ClusterDataListener listener() {
        return new AgentStreamGRPCDataListener(name());
    }
}
