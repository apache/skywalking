package org.skywalking.apm.collector.remote.grpc;

import java.util.Map;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.framework.DefineException;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.core.server.ServerHolder;
import org.skywalking.apm.collector.remote.RemoteModuleDefine;
import org.skywalking.apm.collector.remote.RemoteModuleGroupDefine;

/**
 * @author pengys5
 */
public class RemoteGRPCModuleDefine extends RemoteModuleDefine {
    @Override protected String group() {
        return RemoteModuleGroupDefine.GROUP_NAME;
    }

    @Override public boolean defaultModule() {
        return false;
    }

    @Override protected ModuleConfigParser configParser() {
        return null;
    }

    @Override protected Client createClient(DataMonitor dataMonitor) {
        return null;
    }

    @Override protected Server server() {
        return null;
    }

    @Override protected ModuleRegistration registration() {
        return null;
    }

    @Override public void initialize(Map config, ServerHolder serverHolder) throws DefineException, ClientException {

    }

    @Override public String name() {
        return null;
    }
}
