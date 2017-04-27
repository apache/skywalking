package org.skywalking.apm.collector.config;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.skywalking.apm.collector.cluster.ClusterConfig;

/**
 * @author pengys5
 */
public class ConfigInitializerTestCase {

    @Before
    public void clear() {
        System.clearProperty("cluster.current.HOSTNAME");
        System.clearProperty("cluster.current.PORT");
        System.clearProperty("cluster.current.ROLES");
        System.clearProperty("cluster.SEED_NODES");
    }

    @Test
    public void testInitialize() throws Exception {
        ConfigInitializer.INSTANCE.initialize();

        Assert.assertEquals("127.0.0.1", ClusterConfig.Cluster.Current.HOSTNAME);
        Assert.assertEquals("1000", ClusterConfig.Cluster.Current.PORT);
        Assert.assertEquals("WorkersListener", ClusterConfig.Cluster.Current.ROLES);
        Assert.assertEquals("127.0.0.1:1000", ClusterConfig.Cluster.SEED_NODES);
    }

    @Test
    public void testInitializeWithCli() throws Exception {
        System.setProperty("cluster.current.HOSTNAME", "127.0.0.2");
        System.setProperty("cluster.current.PORT", "1001");
        System.setProperty("cluster.current.ROLES", "Test1, Test2");
        System.setProperty("cluster.SEED_NODES", "127.0.0.1:1000, 127.0.0.1:1001");

        ConfigInitializer.INSTANCE.initialize();

        Assert.assertEquals("127.0.0.2", ClusterConfig.Cluster.Current.HOSTNAME);
        Assert.assertEquals("1001", ClusterConfig.Cluster.Current.PORT);
        Assert.assertEquals("Test1, Test2", ClusterConfig.Cluster.Current.ROLES);
        Assert.assertEquals("127.0.0.1:1000, 127.0.0.1:1001", ClusterConfig.Cluster.SEED_NODES);
    }
}
