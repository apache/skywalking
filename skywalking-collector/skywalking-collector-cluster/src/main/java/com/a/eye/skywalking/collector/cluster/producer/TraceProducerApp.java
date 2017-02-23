package com.a.eye.skywalking.collector.cluster.producer;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.OnSuccess;
import akka.util.Timeout;
import com.a.eye.skywalking.collector.cluster.Const;
import com.a.eye.skywalking.collector.cluster.config.CollectorConfig;
import com.a.eye.skywalking.collector.cluster.config.CollectorConfigInitializer;
import com.a.eye.skywalking.collector.cluster.manager.ActorManagerActor;
import com.a.eye.skywalking.collector.cluster.message.TraceMessages.TransformationJob;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static akka.pattern.Patterns.ask;

/**
 * {@link TraceProducerApp} is a producer for trace agent to send {@link TraceSegment}.
 * <p>
 * Created by pengys5 on 2017/2/17.
 */
public class TraceProducerApp {

    public static void main(String[] args) {
        // Override the configuration of the port when specified as program argument
        final Config config = TraceProducerApp.buildConfig();

        ActorSystem system = ActorSystem.create(CollectorConfig.appname, config);

        system.actorOf(Props.create(ActorManagerActor.class), Const.Actor_Manager_Role);
        final ActorRef frontend = system.actorOf(Props.create(TraceProducerActor.class), Const.Trace_Producer_Role);
        final FiniteDuration interval = Duration.create(2, TimeUnit.SECONDS);
        final Timeout timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS));
        final ExecutionContext ec = system.dispatcher();
        final AtomicInteger counter = new AtomicInteger();

        system.scheduler().schedule(interval, interval, () -> {
            ask(frontend, new TransformationJob("hello-" + counter.incrementAndGet(), null), timeout).onSuccess(new OnSuccess<Object>() {
                public void onSuccess(Object result) {
                    System.out.println(result);
                }
            }, ec);
        }, ec);
    }

    public static Config buildConfig() {
        CollectorConfigInitializer.initialize();

        Config config = ConfigFactory.parseString("akka.actor.provider = akka.cluster.ClusterActorRefProvider")
                .withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.hostname = " + CollectorConfig.Collector.hostname))
                .withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port = " + CollectorConfig.Collector.port))

                .withFallback(ConfigFactory.parseString("akka.remote.log-remote-lifecycle-events = off"))

                .withFallback(ConfigFactory.parseString("akka.cluster.seed-nodes = [" + TraceProducerApp.buildSeedNodes(CollectorConfig.Collector.cluster) + "]"))
                .withFallback(ConfigFactory.parseString("akka.cluster.auto-down-unreachable-after = 10s"))
                .withFallback(ConfigFactory.parseString("akka.cluster.roles = [Actor_Manager_Role, Trace_Producer_Role, Trace_Consumer_Role]"))
                .withFallback(ConfigFactory.parseString("akka.cluster.metrics.enabled = off"));

//                .withFallback(ConfigFactory.load());
        return config;
    }

    public static String buildSeedNodes(String cluster) {
        String[] clusters = cluster.split(",");
        StringBuffer seedNodes = new StringBuffer();
        for (int i = 0; i < clusters.length; i++) {
            if (i > 0) {
                seedNodes.append(",");
            }
            seedNodes.append("\"akka.tcp://").append(CollectorConfig.appname).append("@");
            seedNodes.append(clusters[i]).append("\"");
        }
        return seedNodes.toString();
    }
}
