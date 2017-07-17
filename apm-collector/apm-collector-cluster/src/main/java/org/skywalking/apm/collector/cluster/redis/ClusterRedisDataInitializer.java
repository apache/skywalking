package org.skywalking.apm.collector.cluster.redis;

import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterDataInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ClusterRedisDataInitializer extends ClusterDataInitializer {

    private final Logger logger = LoggerFactory.getLogger(ClusterRedisDataInitializer.class);

    @Override public void addItem(Client client, String itemKey) throws ClientException {
        logger.info("add the redis item key \"{}\" exist", itemKey);
    }

    @Override public boolean existItem(Client client, String itemKey) throws ClientException {
        logger.info("assess the redis item key \"{}\" exist", itemKey);
        return false;
    }
}
