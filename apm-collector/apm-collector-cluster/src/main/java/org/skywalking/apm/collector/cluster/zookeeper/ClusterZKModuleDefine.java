package org.skywalking.apm.collector.cluster.zookeeper;

import org.skywalking.apm.collector.client.zookeeper.ZookeeperClient;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.cluster.ClusterDataInitializer;
import org.skywalking.apm.collector.core.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationWriter;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;
import org.skywalking.apm.collector.core.module.ModuleGroup;

/**
 * @author pengys5
 */
public class ClusterZKModuleDefine extends ClusterModuleDefine {

    @Override protected ModuleGroup group() {
        return ModuleGroup.Cluster;
    }

    @Override public String name() {
        return "zookeeper";
    }

    @Override public boolean defaultModule() {
        return false;
    }

    @Override protected ModuleConfigParser configParser() {
        return new ClusterZKConfigParser();
    }

    @Override protected Client createClient() {
        return new ZookeeperClient(ClusterZKConfig.HOST_PORT, ClusterZKConfig.SESSION_TIMEOUT);
    }

    @Override protected ClusterDataInitializer dataInitializer() {
        return new ClusterZKDataInitializer();
    }

    @Override protected ClusterModuleRegistrationWriter registrationWriter() {
        return new ClusterZKModuleRegistrationWriter(getClient());
    }
}
