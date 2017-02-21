package com.a.eye.skywalking.collector.cluster.manager;

import akka.actor.ActorRef;
import java.util.List;

/**
 * Created by wusheng on 2017/2/21.
 */
public interface RefRouter {
    ActorRef find(List<ActorRef> candidates);
}
