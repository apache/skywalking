package org.skywalking.apm.collector.remote.grpc;

import java.util.Map;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.framework.DataInitializer;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleGroup;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.remote.RemoteModuleDefine;
import org.skywalking.apm.collector.core.server.Server;

/**
 * @author pengys5
 */
public class RemoteGRPCModuleDefine extends RemoteModuleDefine {
    @Override protected ModuleGroup group() {
        return ModuleGroup.Queue;
    }

    @Override public boolean defaultModule() {
        return false;
    }

    @Override protected ModuleConfigParser configParser() {
        return null;
    }

    @Override protected Client createClient() {
        return null;
    }

    @Override protected Server server() {
        return null;
    }

    @Override protected DataInitializer dataInitializer() {
        return null;
    }

    @Override protected ModuleRegistration registration() {
        return null;
    }

    @Override public void initialize(Map config) throws DefineException, ClientException {

    }

    @Override public String name() {
        return null;
    }
}
