package org.skywalking.apm.collector.storage;

import org.skywalking.apm.collector.core.module.ModuleException;

/**
 * @author pengys5
 */
public class StorageModuleException extends ModuleException {
    public StorageModuleException(String message) {
        super(message);
    }

    public StorageModuleException(String message, Throwable cause) {
        super(message, cause);
    }
}
