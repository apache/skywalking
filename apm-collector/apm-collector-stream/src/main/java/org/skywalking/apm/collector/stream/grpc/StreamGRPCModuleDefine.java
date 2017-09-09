package org.skywalking.apm.collector.stream.grpc;

import java.util.ArrayList;
import java.util.List;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.framework.Handler;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.server.grpc.GRPCServer;
import org.skywalking.apm.collector.stream.StreamModuleDefine;
import org.skywalking.apm.collector.stream.StreamModuleGroupDefine;
import org.skywalking.apm.collector.stream.grpc.handler.RemoteCommonServiceHandler;

/**
 * @author pengys5
 */
public class StreamGRPCModuleDefine extends StreamModuleDefine {

    public static final String MODULE_NAME = "grpc";

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override protected String group() {
        return StreamModuleGroupDefine.GROUP_NAME;
    }

    @Override protected ModuleConfigParser configParser() {
        return new StreamGRPCConfigParser();
    }

    @Override protected Client createClient(DataMonitor dataMonitor) {
        return null;
    }

    @Override protected Server server() {
        return new GRPCServer(StreamGRPCConfig.HOST, StreamGRPCConfig.PORT);
    }

    @Override protected ModuleRegistration registration() {
        return new StreamGRPCModuleRegistration();
    }

    @Override public ClusterDataListener listener() {
        return new StreamGRPCDataListener();
    }

    @Override public List<Handler> handlerList() throws DefineException {
        List<Handler> handlers = new ArrayList<>();
        handlers.add(new RemoteCommonServiceHandler());
        return handlers;
    }
}
