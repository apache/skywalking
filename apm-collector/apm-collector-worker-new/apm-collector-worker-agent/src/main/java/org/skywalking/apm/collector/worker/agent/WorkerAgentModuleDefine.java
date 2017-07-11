package org.skywalking.apm.collector.worker.agent;

import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleGroup;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.server.Server;
import org.skywalking.apm.collector.core.worker.WorkerModuleDefine;
import org.skywalking.apm.collector.server.grpc.GRPCServer;

/**
 * @author pengys5
 */
public class WorkerAgentModuleDefine extends WorkerModuleDefine {

    @Override public ModuleGroup group() {
        return ModuleGroup.Worker;
    }

    @Override public String name() {
        return "agent";
    }

    @Override public boolean defaultModule() {
        return true;
    }

    @Override public ModuleConfigParser configParser() {
        return new WorkerAgentConfigParser();
    }

    @Override public Server server() {
        return new GRPCServer(WorkerAgentConfig.HOST, WorkerAgentConfig.PORT);
    }

    @Override protected ModuleRegistration registration() {
        return new WorkerAgentModuleRegistration();
    }
}
