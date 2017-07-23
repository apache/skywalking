package org.skywalking.apm.collector.remote.grpc.handler;

import org.skywalking.apm.collector.core.framework.DefineException;

/**
 * @author pengys5
 */
public class RemoteHandlerDefineException extends DefineException {

    public RemoteHandlerDefineException(String message) {
        super(message);
    }

    public RemoteHandlerDefineException(String message, Throwable cause) {
        super(message, cause);
    }
}
