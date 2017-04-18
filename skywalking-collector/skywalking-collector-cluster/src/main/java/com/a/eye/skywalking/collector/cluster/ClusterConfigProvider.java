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
        if (!StringUtil.isEmpty(System.getProperty("cluster.current.HOSTNAME"))) {
            ClusterConfig.Cluster.Current.HOSTNAME = System.getProperty("cluster.current.HOSTNAME");
        }
        if (!StringUtil.isEmpty(System.getProperty("cluster.current.PORT"))) {
            ClusterConfig.Cluster.Current.PORT = System.getProperty("cluster.current.PORT");
        }
        if (!StringUtil.isEmpty(System.getProperty("cluster.current.ROLES"))) {
            ClusterConfig.Cluster.Current.ROLES = System.getProperty("cluster.current.ROLES");
        }
        if (!StringUtil.isEmpty(System.getProperty("cluster.SEED_NODES"))) {
            ClusterConfig.Cluster.SEED_NODES = System.getProperty("cluster.SEED_NODES");
        }
    }
}
