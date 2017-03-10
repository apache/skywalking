package com.a.eye.skywalking.collector.cluster;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * @author pengys5
 */
public class CollectorConfigTestCase {

    @Before
    public void resetArguments() {
        System.clearProperty("cluster.current.hostname");
        System.clearProperty("cluster.current.port");
        System.clearProperty("cluster.current.roles");
        System.clearProperty("cluster.nodes");
    }

    @Test
    public void testInitializeUseConfigFile() {
        ClusterConfigInitializer.initialize("collector.config");
        Assert.assertEquals("192.168.0.1", ClusterConfig.Cluster.Current.hostname);
        Assert.assertEquals("1000", ClusterConfig.Cluster.Current.port);
        Assert.assertEquals("[Test, Test1]", ClusterConfig.Cluster.Current.roles);
        Assert.assertEquals("[192.168.0.1:1000, 192.168.0.2:1000]", ClusterConfig.Cluster.nodes);
    }

    @Test
    public void testInitializeUseArguments() {
        System.setProperty("cluster.current.hostname", "192.168.0.2");
        System.setProperty("cluster.current.port", "1001");
        System.setProperty("cluster.current.roles", "Test3, Test4");
        System.setProperty("cluster.nodes", "[192.168.0.2:1000, 192.168.0.2:1000]");
        ClusterConfigInitializer.initialize("collector.config");
        Assert.assertEquals("192.168.0.2", ClusterConfig.Cluster.Current.hostname);
        Assert.assertEquals("1001", ClusterConfig.Cluster.Current.port);
        Assert.assertEquals("Test3, Test4", ClusterConfig.Cluster.Current.roles);
        Assert.assertEquals("[192.168.0.2:1000, 192.168.0.2:1000]", ClusterConfig.Cluster.nodes);
    }
}
