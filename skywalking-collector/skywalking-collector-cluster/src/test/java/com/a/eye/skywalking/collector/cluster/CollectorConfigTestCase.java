package com.a.eye.skywalking.collector.cluster;

import com.a.eye.skywalking.collector.cluster.config.CollectorConfig;
import com.a.eye.skywalking.collector.cluster.config.CollectorConfigInitializer;
import com.a.eye.skywalking.collector.cluster.producer.TraceProducerApp;
import com.typesafe.config.Config;
import org.junit.Assert;
import org.junit.Test;

/**
 * Created by pengys5 on 2017/2/22 0022.
 */
public class CollectorConfigTestCase {

    @Test
    public void testConfigInitializer() {
        System.setProperty("collector.hostname", "192.168.0.1");
        System.setProperty("collector.port", "1000");
        System.setProperty("collector.cluster", "192.168.0.1:1000");
        CollectorConfigInitializer.initialize();
        Assert.assertEquals("192.168.0.1", CollectorConfig.Collector.hostname);
        Assert.assertEquals("1000", CollectorConfig.Collector.port);
        Assert.assertEquals("192.168.0.1:1000", CollectorConfig.Collector.cluster);
    }

    @Test
    public void testBuildSeedNodes() {
        String seedNodesContainOne = TraceProducerApp.buildSeedNodes("192.168.0.1:1000");
        Assert.assertEquals("\"akka.tcp://CollectorSystem@192.168.0.1:1000\"", seedNodesContainOne);

        String seedNodesContainTwo = TraceProducerApp.buildSeedNodes("192.168.0.1:1001,192.168.0.2:1002");
        Assert.assertEquals("\"akka.tcp://CollectorSystem@192.168.0.1:1001\",\"akka.tcp://CollectorSystem@192.168.0.2:1002\"", seedNodesContainTwo);

        String seedNodesContainThree = TraceProducerApp.buildSeedNodes("192.168.0.1:1001,192.168.0.2:1002,192.168.0.3:1003");
        Assert.assertEquals("\"akka.tcp://CollectorSystem@192.168.0.1:1001\",\"akka.tcp://CollectorSystem@192.168.0.2:1002\",\"akka.tcp://CollectorSystem@192.168.0.3:1003\"", seedNodesContainThree);
    }

    @Test
    public void testBuildConfig() {
        Config config = TraceProducerApp.buildConfig();

        Assert.assertEquals("akka.cluster.ClusterActorRefProvider", config.getString("akka.actor.provider"));
        Assert.assertEquals("10s", config.getString("akka.cluster.auto-down-unreachable-after"));
        Assert.assertEquals("off", config.getString("akka.cluster.metrics.enabled"));

        Assert.assertEquals("off", config.getString("akka.remote.log-remote-lifecycle-events"));
        Assert.assertEquals("127.0.0.1", config.getString("akka.remote.netty.tcp.hostname"));
        Assert.assertEquals("2551", config.getString("akka.remote.netty.tcp.port"));

        String[] roles = {"Actor_Manager_Role", "Trace_Producer_Role", "Trace_Consumer_Role"};
        Assert.assertArrayEquals(roles, config.getStringList("akka.cluster.roles").toArray());
    }
}
