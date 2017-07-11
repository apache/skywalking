package org.skywalking.apm.collector.cluster.redis;

import org.skywalking.apm.collector.client.redis.RedisClient;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationWriter;
import org.skywalking.apm.collector.core.framework.DataInitializer;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleGroup;

/**
 * @author pengys5
 */
public class ClusterRedisModuleDefine extends ClusterModuleDefine {

    @Override public ModuleGroup group() {
        return ModuleGroup.Cluster;
    }

    @Override public String name() {
        return "redis";
    }

    @Override public boolean defaultModule() {
        return false;
    }

    @Override protected ModuleConfigParser configParser() {
        return new ClusterRedisConfigParser();
    }

    @Override protected Client client() {
        return new RedisClient();
    }

    @Override protected DataInitializer dataInitializer() {
        return new ClusterRedisDataInitializer();
    }

    @Override protected ClusterModuleRegistrationWriter registrationWriter() {
        return new ClusterRedisModuleRegistrationWriter();
    }
}
