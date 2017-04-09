package com.a.eye.skywalking.collector.config;

import com.a.eye.skywalking.collector.cluster.ClusterConfig;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author pengys5
 */
public class ConfigInitializerTestCase {

    @Before
    public void clear() {
        System.clearProperty("cluster.current.hostname");
        System.clearProperty("cluster.current.port");
        System.clearProperty("cluster.current.roles");
        System.clearProperty("cluster.seed_nodes");
    }

    @Test
    public void testInitialize() throws Exception {
        ConfigInitializer.INSTANCE.initialize();

        Assert.assertEquals("127.0.0.1", ClusterConfig.Cluster.Current.hostname);
        Assert.assertEquals("1000", ClusterConfig.Cluster.Current.port);
        Assert.assertEquals("WorkersListener", ClusterConfig.Cluster.Current.roles);
        Assert.assertEquals("127.0.0.1:1000", ClusterConfig.Cluster.seed_nodes);
    }

    @Test
    public void testInitializeWithCli() throws Exception {
        System.setProperty("cluster.current.hostname", "127.0.0.2");
        System.setProperty("cluster.current.port", "1001");
        System.setProperty("cluster.current.roles", "Test1, Test2");
        System.setProperty("cluster.seed_nodes", "127.0.0.1:1000, 127.0.0.1:1001");

        ConfigInitializer.INSTANCE.initialize();

        Assert.assertEquals("127.0.0.2", ClusterConfig.Cluster.Current.hostname);
        Assert.assertEquals("1001", ClusterConfig.Cluster.Current.port);
        Assert.assertEquals("Test1, Test2", ClusterConfig.Cluster.Current.roles);
        Assert.assertEquals("127.0.0.1:1000, 127.0.0.1:1001", ClusterConfig.Cluster.seed_nodes);
    }
}
