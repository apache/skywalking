package com.a.eye.skywalking.collector.worker;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import com.a.eye.skywalking.collector.actor.WorkerRef;
import com.a.eye.skywalking.collector.actor.WorkersCreator;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.cluster.ClusterConfig;
import com.a.eye.skywalking.collector.cluster.ClusterConfigInitializer;
import com.a.eye.skywalking.collector.cluster.WorkersRefCenter;
import com.a.eye.skywalking.collector.worker.receiver.TraceSegmentReceiver;
import com.a.eye.skywalking.sniffer.mock.trace.TraceSegmentBuilderFactory;
import com.a.eye.skywalking.trace.TraceSegment;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;
import org.junit.Test;
import org.powermock.api.support.membermodification.MemberModifier;

import java.util.List;

/**
 * @author pengys5
 */
public class StartUpTestCase {

    public void test() throws Exception {
        ClusterConfigInitializer.initialize("collector.config");
        System.out.println(ClusterConfig.Cluster.Current.roles);

        final Config config = ConfigFactory.parseString("akka.remote.netty.tcp.hostname=" + ClusterConfig.Cluster.Current.hostname).
                withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + ClusterConfig.Cluster.Current.port)).
                withFallback(ConfigFactory.parseString("akka.cluster.roles=" + ClusterConfig.Cluster.Current.roles)).
                withFallback(ConfigFactory.parseString("akka.actor.provider=" + ClusterConfig.Cluster.provider)).
                withFallback(ConfigFactory.parseString("akka.cluster.seed-nodes=" + ClusterConfig.Cluster.nodes)).
                withFallback(ConfigFactory.load());
        ActorSystem system = ActorSystem.create(ClusterConfig.Cluster.appname, config);
        WorkersCreator.INSTANCE.boot(system);

        Thread.sleep(2000);

        for (int i = 0; i < 1; i++) {
            TraceSegment traceSegment = TraceSegmentBuilderFactory.INSTANCE.singleTomcat200Trace();

            List<WorkerRef> availableWorks = WorkersRefCenter.INSTANCE.availableWorks(TraceSegmentReceiver.class.getSimpleName());
            WorkerRef workerRef = RollingSelector.INSTANCE.select(availableWorks, traceSegment);

            ActorRef actorRef = (ActorRef) MemberModifier.field(WorkerRef.class, "actorRef").get(workerRef);
            actorRef.tell(traceSegment, ActorRef.noSender());
        }

        Thread.sleep(10000);
    }
}
