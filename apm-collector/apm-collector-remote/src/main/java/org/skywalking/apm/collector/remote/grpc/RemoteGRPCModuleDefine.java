package org.skywalking.apm.collector.remote.grpc;

import java.util.List;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.config.ConfigException;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.remote.RemoteModuleDefine;
import org.skywalking.apm.collector.remote.RemoteModuleGroupDefine;
import org.skywalking.apm.collector.remote.grpc.handler.RemoteHandlerDefineException;
import org.skywalking.apm.collector.remote.grpc.handler.RemoteHandlerDefineLoader;
import org.skywalking.apm.collector.server.grpc.GRPCServer;

/**
 * @author pengys5
 */
public class RemoteGRPCModuleDefine extends RemoteModuleDefine {

    public static final String MODULE_NAME = "remote";

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override protected String group() {
        return RemoteModuleGroupDefine.GROUP_NAME;
    }

    @Override public boolean defaultModule() {
        return true;
    }

    @Override protected ModuleConfigParser configParser() {
        return new RemoteGRPCConfigParser();
    }

    @Override protected Client createClient(DataMonitor dataMonitor) {
        return null;
    }

    @Override protected Server server() {
        return new GRPCServer(RemoteGRPCConfig.HOST, RemoteGRPCConfig.PORT);
    }

    @Override protected ModuleRegistration registration() {
        return new RemoteGRPCModuleRegistration();
    }

    @Override public ClusterDataListener listener() {
        return new RemoteGRPCDataListener();
    }

    @Override public List<Handler> handlerList() throws DefineException {
        RemoteHandlerDefineLoader loader = new RemoteHandlerDefineLoader();
        List<Handler> handlers = null;
        try {
            handlers = loader.load();
        } catch (ConfigException e) {
            throw new RemoteHandlerDefineException(e.getMessage(), e);
        }
        return handlers;
    }
}
