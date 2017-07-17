package org.skywalking.apm.collector.client.redis;

import org.skywalking.apm.collector.core.client.ClientException;

/**
 * @author pengys5
 */
public class RedisClientException extends ClientException {

    public RedisClientException(String message) {
        super(message);
    }

    public RedisClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
