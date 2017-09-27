package org.skywalking.apm.collector.cluster.standalone;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.zookeeper.util.PathUtils;
import org.skywalking.apm.collector.core.CollectorException;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ClusterStandaloneDataMonitor implements DataMonitor {

    private final Logger logger = LoggerFactory.getLogger(ClusterStandaloneDataMonitor.class);

    private H2Client client;

    private Map<String, ClusterDataListener> listeners;
    private Map<String, ModuleRegistration> registrations;

    public ClusterStandaloneDataMonitor() {
        listeners = new LinkedHashMap<>();
        registrations = new LinkedHashMap<>();
    }

    @Override public void setClient(Client client) {
        this.client = (H2Client)client;
    }

    @Override
    public void addListener(ClusterDataListener listener, ModuleRegistration registration) throws ClientException {
        String path = PathUtils.convertKey2Path(listener.path());
        logger.info("listener path: {}", path);
        listeners.put(path, listener);
        registrations.put(path, registration);
    }

    @Override public ClusterDataListener getListener(String path) {
        path = PathUtils.convertKey2Path(path);
        return listeners.get(path);
    }

    @Override public void createPath(String path) throws ClientException {

    }

    @Override public void setData(String path, String value) throws ClientException {
        if (listeners.containsKey(path)) {
            listeners.get(path).addAddress(value);
            listeners.get(path).serverJoinNotify(value);
        }
    }

    @Override public void start() throws CollectorException {
        Iterator<Map.Entry<String, ModuleRegistration>> entryIterator = registrations.entrySet().iterator();
        while (entryIterator.hasNext()) {
            Map.Entry<String, ModuleRegistration> next = entryIterator.next();
            ModuleRegistration.Value value = next.getValue().buildValue();
            String contextPath = value.getContextPath() == null ? "" : value.getContextPath();
            setData(next.getKey(), value.getHostPort() + contextPath);
        }
    }
}
