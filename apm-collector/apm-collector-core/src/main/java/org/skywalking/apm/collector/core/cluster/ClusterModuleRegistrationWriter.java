package org.skywalking.apm.collector.core.cluster;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public abstract class ClusterModuleRegistrationWriter {

    protected final Client client;

    public ClusterModuleRegistrationWriter(Client client) {
        this.client = client;
    }

    public abstract void write(String key, ModuleRegistration.Value value) throws ClientException;
}
