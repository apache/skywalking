package com.a.eye.skywalking.collector.cluster.base;

import akka.actor.ActorSystem;
import com.a.eye.skywalking.collector.cluster.config.CollectorConfig;

/**
 * @author pengys5
 */
public interface IActorProvider {
    public String actorName();

    public void createActor(ActorSystem system);

    public void actorOf(ActorSystem system, String actorInClusterName);
}
