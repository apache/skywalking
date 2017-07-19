package org.skywalking.apm.collector.agentserver;

import org.skywalking.apm.collector.core.module.ModuleException;

/**
 * @author pengys5
 */
public class AgentServerModuleException extends ModuleException {

    public AgentServerModuleException(String message) {
        super(message);
    }

    public AgentServerModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
