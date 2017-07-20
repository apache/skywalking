package org.skywalking.apm.collector.agentjvm;

import org.skywalking.apm.collector.core.module.ModuleException;

/**
 * @author pengys5
 */
public class AgentJVMModuleException extends ModuleException {
    public AgentJVMModuleException(String message) {
        super(message);
    }

    public AgentJVMModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
