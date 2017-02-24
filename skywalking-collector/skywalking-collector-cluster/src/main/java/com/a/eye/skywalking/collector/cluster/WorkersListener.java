package com.a.eye.skywalking.collector.cluster;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.actor.UntypedActor;

/**
 * @author pengys5
 */
public class WorkersListener extends UntypedActor {

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof WorkerListenerMessage.RegisterMessage) {
            WorkerListenerMessage.RegisterMessage register = (WorkerListenerMessage.RegisterMessage) message;
            ActorRef sender = getSender();
            getContext().watch(sender);

            WorkersRefCenter.INSTANCE.register(sender, register.getRole());
        } else if (message instanceof Terminated) {
            Terminated terminated = (Terminated) message;
            WorkersRefCenter.INSTANCE.unregister(terminated.getActor());
        } else {
            unhandled(message);
        }
    }
}
