package org.skywalking.apm.collector.server.grpc;

import org.skywalking.apm.collector.core.server.ServerException;

/**
 * @author pengys5
 */
public class GRPCServerException extends ServerException {

    public GRPCServerException(String message) {
        super(message);
    }

    public GRPCServerException(String message, Throwable cause) {
        super(message, cause);
    }
}
