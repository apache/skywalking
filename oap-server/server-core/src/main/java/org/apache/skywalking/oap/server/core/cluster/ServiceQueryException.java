package org.apache.skywalking.oap.server.core.cluster;

/**
 * @author caoyixiong
 */
public class ServiceQueryException extends RuntimeException {

    public ServiceQueryException(String message) {
        super(message);
    }
}