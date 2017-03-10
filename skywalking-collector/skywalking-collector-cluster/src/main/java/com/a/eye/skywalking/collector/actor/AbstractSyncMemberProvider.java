package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;

import java.lang.reflect.Constructor;

/**
 * @author pengys5
 */
public abstract class AbstractSyncMemberProvider<T> extends AbstractMemberProvider<T> {

    @Override
    public T createWorker(ActorRef actorRef) throws Exception {
        if (memberClass() == null) {
            throw new IllegalArgumentException("cannot createInstance() with nothing obtained from memberClass()");
        }

        Constructor memberConstructor = memberClass().getDeclaredConstructor(new Class<?>[]{ActorRef.class});
        memberConstructor.setAccessible(true);
        T member = (T) memberConstructor.newInstance(actorRef);
        return member;
    }
}
