package org.skywalking.apm.collector.ui.jetty;

import java.util.LinkedList;
import java.util.List;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.server.jetty.JettyServer;
import org.skywalking.apm.collector.ui.UIModuleDefine;
import org.skywalking.apm.collector.ui.UIModuleGroupDefine;
import org.skywalking.apm.collector.ui.jetty.handler.TraceDagGetHandler;
import org.skywalking.apm.collector.ui.jetty.handler.UIJettyServerHandler;

/**
 * @author pengys5
 */
public class UIJettyModuleDefine extends UIModuleDefine {

    public static final String MODULE_NAME = "jetty";

    @Override protected String group() {
        return UIModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override protected ModuleConfigParser configParser() {
        return new UIJettyConfigParser();
    }

    @Override protected Server server() {
        return new JettyServer(UIJettyConfig.HOST, UIJettyConfig.PORT, UIJettyConfig.CONTEXT_PATH);
    }

    @Override protected ModuleRegistration registration() {
        return new UIJettyModuleRegistration();
    }

    @Override public ClusterDataListener listener() {
        return new UIJettyDataListener();
    }

    @Override public List<Handler> handlerList() {
        List<Handler> handlers = new LinkedList<>();
        handlers.add(new UIJettyServerHandler());
        handlers.add(new TraceDagGetHandler());
        return handlers;
    }
}
