package com.a.eye.skywalking.collector.cluster.manager;

import akka.actor.Terminated;
import com.a.eye.skywalking.collector.cluster.base.AbstractUntypedActor;
import com.a.eye.skywalking.collector.cluster.base.IActorProvider;
import com.a.eye.skywalking.collector.cluster.message.ActorRegisterMessage;

/**
 * Created by Administrator on 2017/2/21 0021.
 */
public class ActorManagerActor extends AbstractUntypedActor {

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof ActorRegisterMessage.RegisterMessage) {
            System.out.println("RegisterMessage");
            ActorRegisterMessage.RegisterMessage regist = (ActorRegisterMessage.RegisterMessage) message;
            getContext().watch(getSender());

            ActorRefCenter.INSTANCE.register(getSender(), regist.getRole());
        } else if (message instanceof Terminated) {
            System.out.println("Terminated");
            Terminated terminated = (Terminated) message;

            ActorRefCenter.INSTANCE.unregister(terminated.getActor());
        } else {
            unhandled(message);
        }
    }
}
