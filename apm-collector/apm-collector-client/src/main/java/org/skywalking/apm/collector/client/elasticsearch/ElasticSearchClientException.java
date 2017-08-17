package org.skywalking.apm.collector.client.elasticsearch;

import org.skywalking.apm.collector.core.client.ClientException;

/**
 * @author pengys5
 */
public class ElasticSearchClientException extends ClientException {
    public ElasticSearchClientException(String message) {
        super(message);
    }

    public ElasticSearchClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
