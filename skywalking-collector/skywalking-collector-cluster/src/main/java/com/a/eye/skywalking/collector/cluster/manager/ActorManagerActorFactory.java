package com.a.eye.skywalking.collector.cluster.manager;

import akka.actor.ActorSystem;
import akka.actor.Props;
import com.a.eye.skywalking.collector.cluster.base.IActorProvider;
import com.a.eye.skywalking.collector.cluster.config.CollectorConfig;

/**
 * @author pengys5
 */
public class ActorManagerActorFactory implements IActorProvider {

    @Override
    public String actorName() {
        return "ActorManagerActor";
    }

    @Override
    public void createActor(ActorSystem system) {
        for (int i = 1; i <= CollectorConfig.Collector.Actor.ActorManagerActor_Num; i++) {
            actorOf(system, actorName() + "_" + i);
        }
    }

    @Override
    public void actorOf(ActorSystem system, String actorInClusterName) {
        system.actorOf(Props.create(ActorManagerActor.class), actorInClusterName);
    }
}
