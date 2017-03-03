package com.a.eye.skywalking.collector.worker;

import akka.actor.ActorSystem;
import com.a.eye.skywalking.collector.actor.WorkersCreator;
import com.a.eye.skywalking.collector.cluster.ClusterConfig;
import com.a.eye.skywalking.collector.cluster.ClusterConfigInitializer;
import com.a.eye.skywalking.collector.cluster.NoAvailableWorkerException;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * @author pengys5
 */
public class CollectorBootStartUp {

    public static void main(String[] args) throws NoAvailableWorkerException, InterruptedException {
        ClusterConfigInitializer.initialize("collector.config");

        final Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + ClusterConfig.Cluster.Current.port).
                withFallback(ConfigFactory.parseString("akka.cluster.roles = [" + ClusterConfig.Cluster.Current.roles + "]")).
                withFallback(ConfigFactory.load());


        ActorSystem system = ActorSystem.create(ClusterConfig.Cluster.appname, config);
        WorkersCreator.INSTANCE.boot(system);
    }
}
