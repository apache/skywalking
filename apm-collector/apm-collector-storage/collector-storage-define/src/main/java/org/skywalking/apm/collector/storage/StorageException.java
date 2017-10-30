package org.skywalking.apm.collector.storage;

import org.skywalking.apm.collector.core.CollectorException;

/**
 * @author peng-yongsheng
 */
public abstract class StorageException extends CollectorException {

    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
