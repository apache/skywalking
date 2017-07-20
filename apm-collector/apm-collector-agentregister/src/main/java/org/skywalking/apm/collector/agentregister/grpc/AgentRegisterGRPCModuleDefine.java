package org.skywalking.apm.collector.agentregister.grpc;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.agentregister.AgentRegisterModuleDefine;
import org.skywalking.apm.collector.agentregister.AgentRegisterModuleGroupDefine;
import org.skywalking.apm.collector.agentregister.grpc.handler.ApplicationRegisterServiceHandler;
import org.skywalking.apm.collector.agentregister.grpc.handler.InstanceDiscoveryServiceHandler;
import org.skywalking.apm.collector.agentregister.grpc.handler.ServiceNameDiscoveryServiceHandler;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.server.grpc.GRPCServer;

/**
 * @author pengys5
 */
public class AgentRegisterGRPCModuleDefine extends AgentRegisterModuleDefine {

    public static final String MODULE_NAME = "grpc";

    @Override protected String group() {
        return AgentRegisterModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override protected ModuleConfigParser configParser() {
        return new AgentRegisterGRPCConfigParser();
    }

    @Override protected Server server() {
        return new GRPCServer(AgentRegisterGRPCConfig.HOST, AgentRegisterGRPCConfig.PORT);
    }

    @Override protected ModuleRegistration registration() {
        return new AgentRegisterGRPCModuleRegistration();
    }

    @Override public ClusterDataListener listener() {
        return new AgentRegisterGRPCDataListener();
    }

    @Override public List<Handler> handlerList() {
        List<Handler> handlers = new LinkedList<>();
        handlers.add(new ApplicationRegisterServiceHandler());
        handlers.add(new InstanceDiscoveryServiceHandler());
        handlers.add(new ServiceNameDiscoveryServiceHandler());
        return handlers;
    }
}
