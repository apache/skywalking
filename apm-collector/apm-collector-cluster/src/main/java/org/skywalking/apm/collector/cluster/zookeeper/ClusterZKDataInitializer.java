package org.skywalking.apm.collector.cluster.zookeeper;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.skywalking.apm.collector.client.zookeeper.ZookeeperClient;
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
        ZookeeperClient zkClient = (ZookeeperClient)client;

        String[] catalogs = itemKey.split("\\.");
        StringBuilder pathBuilder = new StringBuilder();
        for (String catalog : catalogs) {
            pathBuilder.append("/").append(catalog);
            if (zkClient.exists(pathBuilder.toString(), false) == null) {
                zkClient.create(pathBuilder.toString(), null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
            }
        }
    }

    @Override public boolean existItem(Client client, String itemKey) throws ClientException {
        logger.info("assess the zookeeper item key \"{}\" exist", itemKey);
        ZookeeperClient zkClient = (ZookeeperClient)client;

        String[] catalogs = itemKey.split("\\.");
        StringBuilder pathBuilder = new StringBuilder();
        for (String catalog : catalogs) {
            pathBuilder.append("/").append(catalog);
        }

//        if (zkClient.exists(pathBuilder.toString(), false) == null) {
//            return false;
//        } else {
        return true;
//        }
    }
}
