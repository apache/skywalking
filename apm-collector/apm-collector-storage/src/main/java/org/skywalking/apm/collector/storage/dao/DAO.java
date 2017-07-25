package org.skywalking.apm.collector.storage.dao;

import org.skywalking.apm.collector.core.client.Client;

/**
 * @author pengys5
 */
public abstract class DAO<C extends Client> {
    private C client;

    public C getClient() {
        return client;
    }

    public void setClient(C client) {
        this.client = client;
    }
}
