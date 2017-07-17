package org.skywalking.apm.collector.core.framework;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;

/**
 * @author pengys5
 */
public interface DataInitializer {
    void initialize(Client client) throws ClientException;

    void addItem(Client client, String itemKey) throws ClientException;

    boolean existItem(Client client, String itemKey) throws ClientException;
}
