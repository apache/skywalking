package org.skywalking.apm.collector.worker.ui;

import com.google.gson.JsonObject;
import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public class WorkerUIModuleRegistration extends ModuleRegistration {

    @Override public Value buildValue() {
        JsonObject data = new JsonObject();
        data.addProperty("context_path", WorkerUIConfig.CONTEXT_PATH);
        return new Value(WorkerUIConfig.HOST, WorkerUIConfig.PORT, data);
    }
}
