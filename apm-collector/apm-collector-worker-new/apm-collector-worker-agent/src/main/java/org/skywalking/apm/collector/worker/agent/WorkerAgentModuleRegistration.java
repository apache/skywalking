package org.skywalking.apm.collector.worker.agent;

import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public class WorkerAgentModuleRegistration extends ModuleRegistration {

    @Override protected String buildValue() {
        return WorkerAgentConfig.HOST + ModuleRegistration.SEPARATOR + WorkerAgentConfig.PORT;
    }
}
