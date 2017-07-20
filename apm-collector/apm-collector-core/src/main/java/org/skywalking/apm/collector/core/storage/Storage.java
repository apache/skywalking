package org.skywalking.apm.collector.core.storage;

/**
 * @author pengys5
 */
public interface Storage {
    void initialize() throws StorageException;
}
