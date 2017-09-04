package org.skywalking.apm.collector.cluster;

import org.skywalking.apm.collector.core.client.ClientException;

/**
 * @author pengys5
 */
public class ClusterNodeExistException extends ClientException {

    public ClusterNodeExistException(String message) {
        super(message);
    }

    public ClusterNodeExistException(String message, Throwable cause) {
        super(message, cause);
    }
}
