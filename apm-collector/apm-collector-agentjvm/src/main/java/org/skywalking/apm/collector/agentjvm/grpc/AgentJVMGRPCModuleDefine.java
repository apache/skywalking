package org.skywalking.apm.collector.agentjvm.grpc;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.agentjvm.AgentJVMModuleDefine;
import org.skywalking.apm.collector.agentjvm.AgentJVMModuleGroupDefine;
import org.skywalking.apm.collector.agentjvm.grpc.handler.JVMMetricsServiceHandler;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.server.grpc.GRPCServer;

/**
 * @author pengys5
 */
public class AgentJVMGRPCModuleDefine extends AgentJVMModuleDefine {

    public static final String MODULE_NAME = "grpc";

    @Override protected String group() {
        return AgentJVMModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override protected ModuleConfigParser configParser() {
        return new AgentJVMGRPCConfigParser();
    }

    @Override protected Server server() {
        return new GRPCServer(AgentJVMGRPCConfig.HOST, AgentJVMGRPCConfig.PORT);
    }

    @Override protected ModuleRegistration registration() {
        return new AgentJVMGRPCModuleRegistration();
    }

    @Override public ClusterDataListener listener() {
        return new AgentJVMGRPCDataListener();
    }

    @Override public List<Handler> handlerList() {
        List<Handler> handlers = new LinkedList<>();
        handlers.add(new JVMMetricsServiceHandler());
        return handlers;
    }
}
