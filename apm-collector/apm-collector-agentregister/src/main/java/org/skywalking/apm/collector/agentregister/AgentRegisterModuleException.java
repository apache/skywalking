package org.skywalking.apm.collector.agentregister;

import org.skywalking.apm.collector.core.module.ModuleException;

/**
 * @author pengys5
 */
public class AgentRegisterModuleException extends ModuleException {
    public AgentRegisterModuleException(String message) {
        super(message);
    }

    public AgentRegisterModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
