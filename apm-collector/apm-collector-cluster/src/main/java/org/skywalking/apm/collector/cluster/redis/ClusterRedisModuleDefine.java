package org.skywalking.apm.collector.cluster.redis;

import org.skywalking.apm.collector.client.redis.RedisClient;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationWriter;
import org.skywalking.apm.collector.core.framework.DataInitializer;
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

    @Override protected Client createClient() {
        return new RedisClient(ClusterRedisConfig.HOST, ClusterRedisConfig.PORT);
    }

    @Override protected DataInitializer dataInitializer() {
        return new ClusterRedisDataInitializer();
    }

    @Override public ClusterModuleRegistrationWriter registrationWriter() {
        return new ClusterRedisModuleRegistrationWriter(getClient());
    }

    @Override public ClusterModuleRegistrationReader registrationReader() {
        return null;
    }
}
