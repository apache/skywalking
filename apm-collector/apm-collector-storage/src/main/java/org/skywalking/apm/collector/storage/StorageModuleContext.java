package org.skywalking.apm.collector.storage;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.framework.Context;

/**
 * @author pengys5
 */
public class StorageModuleContext extends Context {

    private Client client;

    public StorageModuleContext(String groupName) {
        super(groupName);
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }
}
