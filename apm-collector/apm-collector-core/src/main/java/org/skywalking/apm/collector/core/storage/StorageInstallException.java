package org.skywalking.apm.collector.core.storage;

/**
 * @author pengys5
 */
public class StorageInstallException extends StorageException {

    public StorageInstallException(String message) {
        super(message);
    }

    public StorageInstallException(String message, Throwable cause) {
        super(message, cause);
    }
}
