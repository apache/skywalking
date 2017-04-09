package com.a.eye.skywalking.collector;

import akka.actor.ActorSystem;
import com.a.eye.skywalking.api.util.StringUtil;
import com.a.eye.skywalking.collector.cluster.ClusterConfig;
import com.a.eye.skywalking.collector.cluster.Const;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * @author pengys5
 */
public enum AkkaSystem {
    INSTANCE;
    private Logger logger = LogManager.getFormatterLogger(AkkaSystem.class);

    public ActorSystem create() {
        final Config config = ConfigFactory.parseString("akka.remote.netty.tcp.hostname=" + ClusterConfig.Cluster.Current.hostname).
                withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + ClusterConfig.Cluster.Current.port)).
                withFallback(ConfigFactory.load("application.conf"));
        if (!StringUtil.isEmpty(ClusterConfig.Cluster.seed_nodes)) {
            config.withFallback(ConfigFactory.parseString("akka.cluster.seed-nodes=" + generateSeedNodes()));
        }
        return ActorSystem.create(Const.SystemName, config);
    }

    private String generateSeedNodes() {
        String[] seedNodes = ClusterConfig.Cluster.seed_nodes.split(",");

        String akkaSeedNodes = "";
        for (int i = 0; i < seedNodes.length; i++) {
            String akkaNodeName = "\"akka.tcp://" + Const.SystemName + "@" + seedNodes[i] + "\"";
            if (i > 0) {
                akkaSeedNodes += ",";
            }
            akkaSeedNodes += akkaNodeName;
        }
        akkaSeedNodes = "[" + akkaSeedNodes + "]";
        logger.info("config seedNodes: %s, generate seedNodes: %s", ClusterConfig.Cluster.seed_nodes, akkaSeedNodes);
        return akkaSeedNodes;
    }
}
