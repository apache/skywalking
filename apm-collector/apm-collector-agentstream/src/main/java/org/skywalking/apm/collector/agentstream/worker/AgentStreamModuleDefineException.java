package org.skywalking.apm.collector.agentstream.worker;

import org.skywalking.apm.collector.core.framework.DefineException;

/**
 * @author pengys5
 */
public class AgentStreamModuleDefineException extends DefineException {

    public AgentStreamModuleDefineException(String message) {
        super(message);
    }

    public AgentStreamModuleDefineException(String message, Throwable cause) {
        super(message, cause);
    }
}
