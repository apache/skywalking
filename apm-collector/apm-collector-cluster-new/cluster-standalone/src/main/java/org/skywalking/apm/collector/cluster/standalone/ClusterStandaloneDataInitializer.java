package org.skywalking.apm.collector.cluster.standalone;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterDataInitializer;

/**
 * @author pengys5
 */
public class ClusterStandaloneDataInitializer extends ClusterDataInitializer {

    @Override public void addItem(Client client, String itemKey) throws ClientException {

    }

    @Override public boolean existItem(Client client, String itemKey) throws ClientException {
        return false;
    }
}
