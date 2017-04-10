package com.a.eye.skywalking.collector.cluster;

import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.collector.config.ConfigProvider;

/**
 * @author pengys5
 */
public class ClusterConfigProvider implements ConfigProvider {

    @Override
    public Class configClass() {
        return ClusterConfig.class;
    }

    @Override
    public void cliArgs() {
        if (!StringUtil.isEmpty(System.getProperty("cluster.current.hostname"))) {
            ClusterConfig.Cluster.Current.hostname = System.getProperty("cluster.current.hostname");
        }
        if (!StringUtil.isEmpty(System.getProperty("cluster.current.port"))) {
            ClusterConfig.Cluster.Current.port = System.getProperty("cluster.current.port");
        }
        if (!StringUtil.isEmpty(System.getProperty("cluster.current.roles"))) {
            ClusterConfig.Cluster.Current.roles = System.getProperty("cluster.current.roles");
        }
        if (!StringUtil.isEmpty(System.getProperty("cluster.seed_nodes"))) {
            ClusterConfig.Cluster.seed_nodes = System.getProperty("cluster.seed_nodes");
        }
    }
}
