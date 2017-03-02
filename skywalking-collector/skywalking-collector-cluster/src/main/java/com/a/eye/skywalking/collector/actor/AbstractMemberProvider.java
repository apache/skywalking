package com.a.eye.skywalking.collector.actor;

import akka.actor.ActorRef;

/**
 * @author pengys5
 */
public abstract class AbstractMemberProvider {
    public abstract Class memberClass();

    public void createWorker(MemberSystem system, ActorRef actorRef) {
        if (memberClass() == null) {
            throw new IllegalArgumentException("cannot createInstance() with nothing obtained from memberClass()");
        }

        AbstractMember member = system.memberOf(memberClass(), roleName());
        member.creatorRef(actorRef);
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
