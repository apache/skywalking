package org.skywalking.apm.collector.cluster.redis;

import org.skywalking.apm.collector.client.redis.RedisClient;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationWriter;
import org.skywalking.apm.collector.core.module.ModuleRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author pengys5
 */
public class ClusterRedisModuleRegistrationWriter extends ClusterModuleRegistrationWriter {

    private final Logger logger = LoggerFactory.getLogger(ClusterRedisModuleRegistrationWriter.class);

    public ClusterRedisModuleRegistrationWriter(Client client) {
        super(client);
    }

    @Override public void write(String key, ModuleRegistration.Value value) {
        logger.debug("key {}, value {}", key, value.getHost());
        key = key + "." + value.getHost() + ":" + value.getPort();
        value.getData().addProperty("host", value.getHost());
        value.getData().addProperty("port", value.getPort());
        ((RedisClient)client).setex(key, 120, value.getData().toString());
    }
}
