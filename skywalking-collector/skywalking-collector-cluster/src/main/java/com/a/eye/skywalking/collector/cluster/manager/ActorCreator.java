package com.a.eye.skywalking.collector.cluster.manager;

import akka.actor.ActorSystem;
import com.a.eye.skywalking.collector.cluster.base.IActorProvider;

import java.util.ServiceLoader;

/**
 * @author pengys5
 */
public enum ActorCreator {
    INSTANCE;

    public void create(ActorSystem system) {
        ServiceLoader<IActorProvider> serviceLoader = ServiceLoader.load(IActorProvider.class);
        for (IActorProvider service : serviceLoader) {
            service.createActor(system);
        }
    }
}
