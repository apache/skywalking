package org.skywalking.apm.collector.client.zookeeper;

import java.util.LinkedList;
import java.util.List;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.client.DataListener;
import org.skywalking.apm.collector.core.cluster.ClusterDataInitializer;
import org.skywalking.apm.collector.core.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ZookeeperDataListener implements DataListener, Watcher {

    private final Logger logger = LoggerFactory.getLogger(ZookeeperDataListener.class);

    private Client client;

    public ZookeeperDataListener(Client client) {
        this.client = client;
    }

    @Override public void process(WatchedEvent event) {
        logger.debug("path {}", event.getPath());
        if (StringUtils.isEmpty(event.getPath())) {
            return;
        }

        try {
            String data = client.select(event.getPath());
            logger.debug("data {}", data);
        } catch (ClientException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override public void listen() throws ClientException {
        for (String itemKey : items()) {
            String[] catalogs = itemKey.split("\\.");
            StringBuilder pathBuilder = new StringBuilder();
            for (String catalog : catalogs) {
                pathBuilder.append("/").append(catalog);
            }
            client.listen(pathBuilder.toString());
        }
    }

    @Override public List<String> items() {
        List<String> items = new LinkedList<>();
        items.add(ClusterDataInitializer.FOR_AGENT_CATALOG);
        items.add(ClusterDataInitializer.FOR_UI_CATALOG);
        return items;
    }
}
