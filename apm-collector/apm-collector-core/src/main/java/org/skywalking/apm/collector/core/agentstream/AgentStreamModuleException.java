package org.skywalking.apm.collector.core.agentstream;

import org.skywalking.apm.collector.core.module.ModuleException;

/**
 * @author pengys5
 */
public class AgentStreamModuleException extends ModuleException {
    public AgentStreamModuleException(String message) {
        super(message);
    }

    public AgentStreamModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
