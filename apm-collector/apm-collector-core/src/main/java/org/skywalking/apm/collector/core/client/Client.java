package org.skywalking.apm.collector.core.client;

/**
 * @author pengys5
 */
public interface Client {
    void initialize() throws ClientException;

    void shutdown();
}
