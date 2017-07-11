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

    @Override public ModuleGroup group() {
        return ModuleGroup.Cluster;
    }

    @Override public String name() {
        return "zookeeper";
    }

    @Override public boolean defaultModule() {
        return false;
    }

    @Override public ModuleConfigParser configParser() {
        return new ClusterZKConfigParser();
    }

    @Override public Client client() {
        return new ZookeeperClient();
    }

    @Override public ClusterDataInitializer dataInitializer() {
        return new ClusterZKDataInitializer();
    }

    @Override protected ClusterModuleRegistrationWriter registrationWriter() {
        return new ClusterZKModuleRegistrationWriter();
    }
}
