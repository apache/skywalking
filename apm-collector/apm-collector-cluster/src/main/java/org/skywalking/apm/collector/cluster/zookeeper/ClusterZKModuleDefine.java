package org.skywalking.apm.collector.cluster.zookeeper;

import org.apache.zookeeper.Watcher;
import org.skywalking.apm.collector.client.zookeeper.ZookeeperClient;
import org.skywalking.apm.collector.cluster.ClusterModuleDefine;
import org.skywalking.apm.collector.cluster.ClusterModuleGroupDefine;
import org.skywalking.apm.collector.core.client.Client;
import org.skywalking.apm.collector.core.client.DataMonitor;
import org.skywalking.apm.collector.core.cluster.ClusterModuleRegistrationReader;
import org.skywalking.apm.collector.core.module.ModuleConfigParser;

/**
 * @author pengys5
 */
public class ClusterZKModuleDefine extends ClusterModuleDefine {

    public static final String MODULE_NAME = "zookeeper";

    @Override protected String group() {
        return ClusterModuleGroupDefine.GROUP_NAME;
    }

    @Override public String name() {
        return MODULE_NAME;
    }

    @Override public boolean defaultModule() {
        return false;
    }

    @Override protected ModuleConfigParser configParser() {
        return new ClusterZKConfigParser();
    }

    @Override public DataMonitor dataMonitor() {
        return new ClusterZKDataMonitor();
    }

    @Override protected Client createClient(DataMonitor dataMonitor) {
        return new ZookeeperClient(ClusterZKConfig.HOST_PORT, ClusterZKConfig.SESSION_TIMEOUT, (Watcher)dataMonitor);
    }

    @Override public ClusterModuleRegistrationReader registrationReader(DataMonitor dataMonitor) {
        return new ClusterZKModuleRegistrationReader(dataMonitor);
    }
}
