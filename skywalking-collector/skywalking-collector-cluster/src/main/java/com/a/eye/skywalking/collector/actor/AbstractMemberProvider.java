package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;

/**
 * @author pengys5
 */
public abstract class AbstractMemberProvider<T> {

    public abstract Class memberClass();

    public abstract T createWorker(ActorRef actorRef) throws Exception;
}
