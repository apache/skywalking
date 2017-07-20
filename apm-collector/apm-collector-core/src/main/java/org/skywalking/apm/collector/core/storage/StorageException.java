package org.skywalking.apm.collector.core.storage;

import org.skywalking.apm.collector.core.CollectorException;

/**
 * @author pengys5
 */
public abstract class StorageException extends CollectorException {
    public StorageException(String message) {
        super(message);
    }

    public StorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
