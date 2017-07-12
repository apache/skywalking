package org.skywalking.apm.collector.cluster.standalone;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterDataInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ClusterStandaloneDataInitializer extends ClusterDataInitializer {

    private final Logger logger = LoggerFactory.getLogger(ClusterStandaloneDataInitializer.class);

    @Override public void addItem(Client client, String itemKey) throws ClientException {
        logger.info("add the h2 item key \"{}\" exist", itemKey);
        itemKey = itemKey.replaceAll("\\.", "_");
        String sql = "CREATE TABLE " + itemKey + "(ADDRESS VARCHAR(100) PRIMARY KEY,DATA VARCHAR(255));";
        try {
            ((H2Client)client).execute(sql);
        } catch (H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
    }

    @Override public boolean existItem(Client client, String itemKey) throws ClientException {
        return false;
    }
}
