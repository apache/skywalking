package org.skywalking.apm.collector.cluster.standalone;

import org.skywalking.apm.collector.client.h2.H2Client;
import org.skywalking.apm.collector.client.h2.H2ClientException;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationWriter;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ClusterStandaloneModuleRegistrationWriter extends ClusterModuleRegistrationWriter {

    private final Logger logger = LoggerFactory.getLogger(ClusterStandaloneModuleRegistrationWriter.class);

    public ClusterStandaloneModuleRegistrationWriter(Client client) {
        super(client);
    }

    @Override public void write(String key, ModuleRegistration.Value value) {
        key = key.replaceAll("\\.", "_");
        String hostPort = value.getHost() + ":" + value.getPort();
        String sql = "INSERT INTO " + key + " VALUES('" + hostPort + "', '" + value.getData().toString() + "');";
        String sql2 = "SELECT * FROM " + key;
        try {
            ((H2Client)client).execute(sql);
            ((H2Client)client).executeQuery(sql2);
        } catch (H2ClientException e) {
            logger.error(e.getMessage(), e);
        }
    }
}
