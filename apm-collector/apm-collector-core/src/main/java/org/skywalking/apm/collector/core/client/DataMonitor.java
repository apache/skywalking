package org.skywalking.apm.collector.core.client;

import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.module.ModuleRegistration;

/**
 * @author pengys5
 */
public interface DataMonitor {
    void setClient(Client client);

    void addListener(ClusterDataListener listener, ModuleRegistration registration) throws ClientException;

    ClusterDataListener getListener(String path);

    void createPath(String path) throws ClientException;

    void setData(String path, String value) throws ClientException;
}
