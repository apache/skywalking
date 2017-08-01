package org.skywalking.apm.collector.cluster.redis;

import org.skywalking.apm.collector.client.redis.RedisClient;
import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;

/**
 * @author pengys5
 */
public class ClusterRedisModuleDefine extends ClusterModuleDefine {

    public static final String MODULE_NAME = "redis";

    @Override public String group() {
        return ClusterModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override public boolean defaultModule() {
        return false;
    }

    @Override protected ModuleConfigParser configParser() {
        return new ClusterRedisConfigParser();
    }

    @Override public DataMonitor dataMonitor() {
        return null;
    }

    @Override protected Client createClient(DataMonitor dataMonitor) {
        return new RedisClient(ClusterRedisConfig.HOST, ClusterRedisConfig.PORT);
    }

    @Override public ClusterModuleRegistrationReader registrationReader(DataMonitor dataMonitor) {
        return new ClusterRedisModuleRegistrationReader(dataMonitor);
    }
}
