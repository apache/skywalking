package org.skywalking.apm.collector.agentserver.jetty;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.agentserver.AgentServerModuleDefine;
import org.skywalking.apm.collector.agentserver.AgentServerModuleGroupDefine;
import org.skywalking.apm.collector.agentserver.jetty.handler.AgentStreamGRPCServerHandler;
import org.skywalking.apm.collector.agentserver.jetty.handler.AgentStreamJettyServerHandler;
import org.skywalking.apm.collector.agentserver.jetty.handler.UIJettyServerHandler;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.server.jetty.JettyServer;

/**
 * @author pengys5
 */
public class AgentServerJettyModuleDefine extends AgentServerModuleDefine {

    public static final String MODULE_NAME = "jetty";

    @Override protected String group() {
        return AgentServerModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override public boolean defaultModule() {
        return true;
    }

    @Override protected ModuleConfigParser configParser() {
        return new AgentServerJettyConfigParser();
    }

    @Override protected Server server() {
        return new JettyServer(AgentServerJettyConfig.HOST, AgentServerJettyConfig.PORT, AgentServerJettyConfig.CONTEXT_PATH);
    }

    @Override protected ModuleRegistration registration() {
        return new AgentServerJettyModuleRegistration();
    }

    @Override public ClusterDataListener listener() {
        return new AgentServerJettyDataListener();
    }

    @Override public List<Handler> handlerList() {
        List<Handler> handlers = new LinkedList<>();
        handlers.add(new AgentStreamGRPCServerHandler());
        handlers.add(new AgentStreamJettyServerHandler());
        handlers.add(new UIJettyServerHandler());
        return handlers;
    }
}
