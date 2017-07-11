package org.skywalking.apm.collector.worker.ui;

import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public class WorkerUIModuleRegistration extends ModuleRegistration {

    @Override protected String buildValue() {
        return WorkerUIConfig.HOST + ModuleRegistration.SEPARATOR + WorkerUIConfig.PORT + ModuleRegistration.SEPARATOR + WorkerUIConfig.CONTEXT_PATH;
    }
}
