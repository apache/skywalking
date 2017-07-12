package org.skywalking.apm.collector.worker.agent;

import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public class WorkerAgentModuleRegistration extends ModuleRegistration {

    @Override public Value buildValue() {
        return new Value(WorkerAgentConfig.HOST, WorkerAgentConfig.PORT, null);
    }
}
