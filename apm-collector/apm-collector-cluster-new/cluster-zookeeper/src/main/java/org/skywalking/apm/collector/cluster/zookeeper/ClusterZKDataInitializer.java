package org.skywalking.apm.collector.cluster.zookeeper;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterDataInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ClusterZKDataInitializer extends ClusterDataInitializer {

    private final Logger logger = LoggerFactory.getLogger(ClusterZKDataInitializer.class);

    @Override public void addItem(Client client, String itemKey) throws ClientException {
        logger.info("add the zookeeper item key \"{}\" exist", itemKey);
        String[] catalogs = itemKey.split("\\.");
        StringBuilder pathBuilder = new StringBuilder();
        for (String catalog : catalogs) {
            pathBuilder.append("/").append(catalog);
            if (!client.exist(pathBuilder.toString())) {
                client.insert(pathBuilder.toString());
            }
        }
    }

    @Override public boolean existItem(Client client, String itemKey) throws ClientException {
        logger.info("assess the zookeeper item key \"{}\" exist", itemKey);
        String[] catalogs = itemKey.split("\\.");
        StringBuilder pathBuilder = new StringBuilder();
        for (String catalog : catalogs) {
            pathBuilder.append("/").append(catalog);
        }
        return client.exist(pathBuilder.toString());
    }
}
