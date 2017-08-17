package org.skywalking.apm.collector.client.grpc;

import org.skywalking.apm.collector.core.client.ClientException;

/**
 * @author pengys5
 */
public class GRPCClientException extends ClientException {

    public GRPCClientException(String message) {
        super(message);
    }

    public GRPCClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
