package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;

import java.lang.reflect.Constructor;

/**
 * @author pengys5
 */
public abstract class AbstractSyncMemberProvider<T> {

    public abstract Class memberClass();

    public T createWorker(ActorRef actorRef) throws Exception {
        if (memberClass() == null) {
            throw new IllegalArgumentException("cannot createInstance() with nothing obtained from memberClass()");
        }

        Constructor memberConstructor = memberClass().getDeclaredConstructor(new Class[]{ActorRef.class});
        memberConstructor.setAccessible(true);
        T member = (T) memberConstructor.newInstance(actorRef);
        return member;
    }

    /**
     * Use {@link #memberClass()} method returned class's simple name as a role name.
     *
     * @return is role of Worker
     */
    protected String roleName() {
        return memberClass().getSimpleName();
    }
}
