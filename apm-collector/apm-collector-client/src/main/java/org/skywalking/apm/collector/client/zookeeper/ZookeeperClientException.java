package org.skywalking.apm.collector.client.zookeeper;

import org.skywalking.apm.collector.core.client.ClientException;

/**
 * @author pengys5
 */
public class ZookeeperClientException extends ClientException {
    public ZookeeperClientException(String message) {
        super(message);
    }

    public ZookeeperClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
