package org.skywalking.apm.collector.storage.elasticsearch;

import org.skywalking.apm.collector.core.storage.StorageException;

/**
 * @author pengys5
 */
public class ElasticSearchStorageException extends StorageException {
    public ElasticSearchStorageException(String message) {
        super(message);
    }

    public ElasticSearchStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
