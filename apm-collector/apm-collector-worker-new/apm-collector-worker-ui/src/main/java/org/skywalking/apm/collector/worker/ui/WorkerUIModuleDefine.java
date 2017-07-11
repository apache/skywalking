package org.skywalking.apm.collector.worker.ui;

import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleGroup;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.core.worker.WorkerModuleDefine;
import org.skywalking.apm.collector.server.jetty.JettyServer;

/**
 * @author pengys5
 */
public class WorkerUIModuleDefine extends WorkerModuleDefine {

    @Override public ModuleGroup group() {
        return ModuleGroup.Worker;
    }

    @Override public String name() {
        return "ui";
    }

    @Override public boolean defaultModule() {
        return true;
    }

    @Override public ModuleConfigParser configParser() {
        return new WorkerUIConfigParser();
    }

    @Override public Server server() {
        return new JettyServer(WorkerUIConfig.HOST, WorkerUIConfig.PORT, WorkerUIConfig.CONTEXT_PATH);
    }

    @Override protected ModuleRegistration registration() {
        return new WorkerUIModuleRegistration();
    }
}
