package org.skywalking.apm.collector.cluster.zookeeper;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.data.Stat;
import org.skywalking.apm.collector.client.zookeeper.ZookeeperClient;
import org.skywalking.apm.collector.client.zookeeper.util.PathUtils;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.ClientException;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationWriter;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ClusterZKModuleRegistrationWriter extends ClusterModuleRegistrationWriter {

    private final Logger logger = LoggerFactory.getLogger(ClusterZKModuleRegistrationWriter.class);

    public ClusterZKModuleRegistrationWriter(Client client) {
        super(client);
    }

    @Override public void write(String key, ModuleRegistration.Value value) throws ClientException {
        logger.info("cluster zookeeper register key: {}, value: {}", key, value);
        String workerUIPath = PathUtils.convertKey2Path(key) + "/" + value.getHost() + ":" + value.getPort();

        Stat stat = ((ZookeeperClient)client).exists(workerUIPath, false);
        if (stat == null) {
            ((ZookeeperClient)client).create(workerUIPath, value.getData() == null ? null : value.getData().toString().getBytes(), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        } else {
            ((ZookeeperClient)client).setData(workerUIPath, value.getData() == null ? null : value.getData().toString().getBytes(), -1);
        }
    }
}
