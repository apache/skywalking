package com.a.eye.skywalking.collector.worker;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.a.eye.skywalking.collector.cluster.Const;
import com.a.eye.skywalking.collector.cluster.consumer.TraceConsumerActor;

/**
 * @author pengys5
 */
public class CollectorBootStartUp {

    public static void main(String[] args) {
//        ActorSystem system = ActorSystem.create("ClusterSystem", config);
//        system.actorOf(Props.create(TraceConsumerActor.class), Const.Trace_Consumer_Role);
    }
}
