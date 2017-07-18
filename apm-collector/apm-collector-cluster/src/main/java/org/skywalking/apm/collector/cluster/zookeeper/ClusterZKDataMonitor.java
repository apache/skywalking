package org.skywalking.apm.collector.cluster.zookeeper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.skywalking.apm.collector.client.zookeeper.ZookeeperClient;
import org.skywalking.apm.collector.client.zookeeper.ZookeeperClientException;
import org.skywalking.apm.collector.client.zookeeper.util.PathUtils;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterDataListener;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.skywalking.apm.collector.core.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ClusterZKDataMonitor implements DataMonitor, Watcher {

    private final Logger logger = LoggerFactory.getLogger(ClusterZKDataMonitor.class);

    private ZookeeperClient client;

    private Map<String, ClusterDataListener> listeners;

    public ClusterZKDataMonitor() {
        listeners = new LinkedHashMap<>();
    }

    @Override public void process(WatchedEvent event) {
        logger.debug("changed path {}", event.getPath());
        if (listeners.containsKey(event.getPath())) {
            putDataIntoListener(listeners.get(event.getPath()), event.getPath());
        }
    }

    @Override public void setClient(Client client) {
        this.client = (ZookeeperClient)client;
    }

    @Override
    public void addListener(ClusterDataListener listener, ModuleRegistration registration) throws ClientException {
        String path = PathUtils.convertKey2Path(listener.path());
        logger.info("listener path: {}", path);
        listeners.put(path, listener);
        createPath(path);
        List<String> paths = client.getChildren(path, true);

        if (CollectionUtils.isNotEmpty(paths)) {
            paths.forEach(subPath -> {
                putDataIntoListener(listener, subPath);
            });
        }

        ModuleRegistration.Value value = registration.buildValue();
        setData(path + "/" + value.getHostPort(), value.getData() == null ? "" : value.getData().toString());
    }

    @Override public void createPath(String path) throws ClientException {
        String[] paths = path.replaceFirst("/", "").split("/");

        StringBuilder pathBuilder = new StringBuilder();
        for (String subPath : paths) {
            pathBuilder.append("/").append(subPath);
            if (client.exists(pathBuilder.toString(), false) == null) {
                client.create(pathBuilder.toString(), null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        }
    }

    @Override public void setData(String path, String value) throws ClientException {
        if (client.exists(path, false) == null) {
            client.create(path, value.getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        } else {
            client.setData(path, value.getBytes(), -1);
        }
    }

    private void putDataIntoListener(ClusterDataListener listener, String path) {
        try {
            byte[] data = client.getData(path, false, null);
            String dataStr = String.valueOf(data);
            listener.setData(new ClusterDataListener.Data(path, dataStr));
        } catch (ZookeeperClientException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
