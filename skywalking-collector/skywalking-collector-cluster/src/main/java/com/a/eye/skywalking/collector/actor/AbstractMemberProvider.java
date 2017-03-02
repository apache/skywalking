package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;

import java.lang.reflect.Constructor;

/**
 * @author pengys5
 */
public abstract class AbstractMemberProvider {
    public abstract Class memberClass();

    public void createWorker(MemberSystem system, ActorRef actorRef) throws Exception {
        if (memberClass() == null) {
            throw new IllegalArgumentException("cannot createInstance() with nothing obtained from memberClass()");
        }

        Constructor memberConstructor = memberClass().getDeclaredConstructor(new Class[]{MemberSystem.class, ActorRef.class});
        memberConstructor.setAccessible(true);
        AbstractMember member = (AbstractMember) memberConstructor.newInstance(system, actorRef);
        system.memberOf(member, roleName());
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
