package com.a.eye.skywalking.collector.worker.indicator;

import akka.actor.UntypedActor;
import com.a.eye.skywalking.collector.cluster.base.AbstractUntypedActor;

/**
 * @author pengys5
 */
public class ApplicationDiscoverActor extends AbstractUntypedActor {

    public static final String ActorName = "ApplicationDiscoverActor";

    @Override
    public String actorName() {
        return ActorName;
    }

    @Override
    public void onReceive(Object message) throws Throwable {

    }
}
