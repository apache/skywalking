package com.a.eye.skywalking.collector.cluster.producer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.a.eye.skywalking.collector.cluster.Const;
import com.a.eye.skywalking.collector.cluster.consumer.TraceConsumerActor;
import com.a.eye.skywalking.collector.cluster.manager.ActorManagerActor;
import com.a.eye.skywalking.sniffer.mock.trace.TraceSegmentBuilderFactory;
import com.a.eye.skywalking.trace.TraceSegment;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import com.a.eye.skywalking.collector.cluster.message.TraceMessages.TransformationJob;
import scala.concurrent.ExecutionContext;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.dispatch.OnSuccess;
import akka.util.Timeout;

import static akka.pattern.Patterns.ask;

public class TraceProducerApp {

    public static void main(String[] args) {
        // Override the configuration of the port when specified as program argument
        final String port = args.length > 0 ? args[0] : "2552";
        final Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
        withFallback(ConfigFactory.load());

        ActorSystem system = ActorSystem.create("ClusterSystem", config);

        system.actorOf(Props.create(ActorManagerActor.class), Const.Actor_Manager_Role);
        final ActorRef frontend = system.actorOf(Props.create(TraceProducerActor.class), Const.Trace_Producer_Role);
        final FiniteDuration interval = Duration.create(2, TimeUnit.SECONDS);
        final Timeout timeout = new Timeout(Duration.create(5, TimeUnit.SECONDS));
        final ExecutionContext ec = system.dispatcher();
        final AtomicInteger counter = new AtomicInteger();
        system.scheduler().schedule(interval, interval, new Runnable() {
            public void run() {
//                TraceSegment traceSegment = TraceSegmentBuilderFactory.INSTANCE.singleTomcat200Trace();
                ask(frontend,
                        new TransformationJob("hello-" + counter.incrementAndGet(), null),
                        timeout).onSuccess(new OnSuccess<Object>() {
                    public void onSuccess(Object result) {
                        System.out.println(result);
                    }
                }, ec);
            }

        }, ec);

    }
}
