package com.a.eye.skywalking.collector.worker;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import com.a.eye.skywalking.collector.cluster.ClusterConfig;
import com.a.eye.skywalking.collector.cluster.ClusterConfigInitializer;
import com.a.eye.skywalking.collector.worker.receiver.TraceSegmentReceiver;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.sniffer.mock.trace.TraceSegmentBuilderFactory;
import com.a.eye.skywalking.trace.TraceSegment;
import com.a.eye.skywalking.trace.proto.SegmentMessage;
import com.a.eye.skywalking.trace.proto.SegmentRefMessage;
import com.a.eye.skywalking.trace.tag.Tags;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

/**
 * @author pengys5
 */
public class StartUpTestCase {

    public void test() throws Exception {
        System.out.println(TraceSegmentReceiver.class.getSimpleName());
        ClusterConfigInitializer.initialize("collector.config");
        System.out.println(ClusterConfig.Cluster.Current.roles);

        final Config config = ConfigFactory.parseString("akka.remote.netty.tcp.hostname=" + ClusterConfig.Cluster.Current.hostname).
                withFallback(ConfigFactory.parseString("akka.remote.netty.tcp.port=" + ClusterConfig.Cluster.Current.port)).
                withFallback(ConfigFactory.parseString("akka.cluster.roles=" + ClusterConfig.Cluster.Current.roles)).
                withFallback(ConfigFactory.parseString("akka.actor.provider=" + ClusterConfig.Cluster.provider)).
                withFallback(ConfigFactory.parseString("akka.cluster.seed-nodes=" + ClusterConfig.Cluster.nodes)).
                withFallback(ConfigFactory.load("application.conf"));
        ActorSystem system = ActorSystem.create(ClusterConfig.Cluster.appname, config);
//        WorkersCreator.INSTANCE.boot(system);

        EsClient.boot();

        TraceSegment dubboClientData = TraceSegmentBuilderFactory.INSTANCE.traceOf_Tomcat_DubboClient();

        SegmentMessage.Builder clientBuilder = dubboClientData.serialize().toBuilder();
        clientBuilder.setApplicationCode("Tomcat_DubboClient");

        dubboClientData = new TraceSegment(clientBuilder.build());

        TraceSegment dubboServerData = TraceSegmentBuilderFactory.INSTANCE.traceOf_DubboServer_MySQL();

        SegmentMessage serializeServer = dubboServerData.serialize();
        SegmentMessage.Builder builder = serializeServer.toBuilder();

        SegmentRefMessage.Builder builderRef = builder.getRefs(0).toBuilder();
        builderRef.setApplicationCode(dubboClientData.getApplicationCode());


        builderRef.setPeerHost(Tags.PEER_HOST.get(dubboClientData.getSpans().get(1)));

        builder.setApplicationCode("DubboServer_MySQL");
        builder.addRefs(builderRef);
        dubboServerData = new TraceSegment(builder.build());

        Thread.sleep(5000);

        ActorSelection selection = system.actorSelection("/user/TraceSegmentReceiver_1");

        for (int i = 0; i < 100; i++) {
            selection.tell(dubboClientData, ActorRef.noSender());
            selection.tell(dubboServerData, ActorRef.noSender());

            Thread.sleep(200);
        }

        Thread.sleep(1000000);
    }
}
