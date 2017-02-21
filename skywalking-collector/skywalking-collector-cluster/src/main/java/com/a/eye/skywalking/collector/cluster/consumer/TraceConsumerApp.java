package com.a.eye.skywalking.collector.cluster.consumer;

import com.a.eye.skywalking.collector.cluster.Const;
import com.a.eye.skywalking.collector.cluster.manager.ActorManagerActor;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;
import akka.actor.Props;

public class TraceConsumerApp {

    public static void main(String[] args) throws InterruptedException {
        // Override the configuration of the port when specified as program argument
        final String port = args.length > 0 ? args[0] : "2551";
        final Config config = ConfigFactory.parseString("akka.remote.netty.tcp.port=" + port).
        withFallback(ConfigFactory.load());

        ActorSystem system = ActorSystem.create("ClusterSystem", config);

//        system.actorOf(Props.create(ActorManagerActor.class), Const.Actor_Manager_Role);
        system.actorOf(Props.create(TraceConsumerActor.class), Const.Trace_Consumer_Role);
    }

}
