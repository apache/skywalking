package org.skywalking.apm.collector.agentregister.jetty;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.agentregister.AgentRegisterModuleDefine;
import org.skywalking.apm.collector.agentregister.AgentRegisterModuleGroupDefine;
import org.skywalking.apm.collector.agentregister.jetty.handler.ApplicationRegisterServletHandler;
import org.skywalking.apm.collector.agentregister.jetty.handler.InstanceDiscoveryServletHandler;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.server.jetty.JettyServer;

/**
 * @author pengys5
 */
public class AgentRegisterJettyModuleDefine extends AgentRegisterModuleDefine {

    public static final String MODULE_NAME = "jetty";

    @Override protected String group() {
        return AgentRegisterModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override protected ModuleConfigParser configParser() {
        return new AgentRegisterJettyConfigParser();
    }

    @Override protected Server server() {
        return new JettyServer(AgentRegisterJettyConfig.HOST, AgentRegisterJettyConfig.PORT, AgentRegisterJettyConfig.CONTEXT_PATH);
    }

    @Override protected ModuleRegistration registration() {
        return new AgentRegisterJettyModuleRegistration();
    }

    @Override public ClusterDataListener listener() {
        return new AgentRegisterJettyDataListener();
    }

    @Override public List<Handler> handlerList() {
        List<Handler> handlers = new LinkedList<>();
        handlers.add(new ApplicationRegisterServletHandler());
        handlers.add(new InstanceDiscoveryServletHandler());
        return handlers;
    }
}
