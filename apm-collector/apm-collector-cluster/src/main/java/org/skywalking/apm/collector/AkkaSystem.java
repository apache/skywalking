package org.skywalking.apm.collector;

import akka.actor.ActorSystem;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.util.StringUtil;
import org.skywalking.apm.collector.cluster.ClusterConfig;
import org.skywalking.apm.collector.cluster.Const;

/**
 * @author pengys5
 */
public enum AkkaSystem {
    INSTANCE;
    private Logger logger = LogManager.getFormatterLogger(AkkaSystem.class);

    public ActorSystem create() {
        Level logLevel = logger.getLevel();

        final Config config = ConfigFactory.parseString("akka.remote.netty.tcp.HOSTNAME=" + ClusterConfig.Cluster.Current.HOSTNAME).
            withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.PORT=" + ClusterConfig.Cluster.Current.PORT)).
            withFallback(ConfigFactory.parseString("akka.loggers=[\"akka.event.slf4j.Slf4jLogger\"]")).
            withFallback(ConfigFactory.parseString("akka.loglevel=\"" + logLevel.name() + "\"")).

            withFallback(ConfigFactory.load("application.conf"));
        if (!StringUtil.isEmpty(ClusterConfig.Cluster.SEED_NODES)) {
            config.withFallback(ConfigFactory.parseString("akka.cluster.seed-nodes=" + generateSeedNodes()));
        }
        return ActorSystem.create(Const.SYSTEM_NAME, config);
    }

    private String generateSeedNodes() {
        String[] seedNodes = ClusterConfig.Cluster.SEED_NODES.split(",");

        String akkaSeedNodes = "";
        for (int i = 0; i < seedNodes.length; i++) {
            String akkaNodeName = "\"akka.tcp://" + Const.SYSTEM_NAME + "@" + seedNodes[i] + "\"";
            if (i > 0) {
                akkaSeedNodes += ",";
            }
            akkaSeedNodes += akkaNodeName;
        }
        akkaSeedNodes = "[" + akkaSeedNodes + "]";
        logger.info("config seedNodes: %s, generate seedNodes: %s", ClusterConfig.Cluster.SEED_NODES, akkaSeedNodes);
        return akkaSeedNodes;
    }
}
