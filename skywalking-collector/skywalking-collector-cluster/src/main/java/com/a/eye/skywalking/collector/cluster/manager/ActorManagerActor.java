package com.a.eye.skywalking.collector.cluster.manager;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import com.a.eye.skywalking.collector.cluster.message.ActorRegisteMessage;

import java.util.*;

/**
 * Created by Administrator on 2017/2/21 0021.
 */
public class ActorManagerActor extends UntypedActor {

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof ActorRegisteMessage.RegisteMessage) {
            System.out.println("RegisteMessage");
            ActorRegisteMessage.RegisteMessage regist = (ActorRegisteMessage.RegisteMessage) message;
            getContext().watch(getSender());
            if (!ActorCache.roleToActor.containsKey(regist.getRole())) {
                List<ActorRef> actorList = Collections.synchronizedList(new ArrayList<ActorRef>());
                ActorCache.roleToActor.putIfAbsent(regist.getRole(), actorList);
            }
            getContext().watch(getSender());
            ActorCache.roleToActor.get(regist.getRole()).add(getSender());
            ActorCache.actorToRole.put(getSender(), regist.getRole());
        } else if (message instanceof Terminated) {
            System.out.println("Terminated");
            Terminated terminated = (Terminated) message;
            String role = ActorCache.actorToRole.get(terminated.getActor());
            ActorCache.roleToActor.get(role).remove(terminated.getActor());
            ActorCache.actorToRole.remove(terminated.getActor());
        } else {
            unhandled(message);
        }
    }
}
