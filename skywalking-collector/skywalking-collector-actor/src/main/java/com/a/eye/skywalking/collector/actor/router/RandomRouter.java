package com.a.eye.skywalking.collector.actor.router;

import akka.actor.ActorRef;
import com.a.eye.skywalking.collector.cluster.WorkersRefCenter;

import java.util.List;
import java.util.Random;

/**
 * @author pengys5
 */
public class RandomRouter implements WorkerRouter {

    @Override
    public ActorRef find(String workerRole) {
        int workerNum = WorkersRefCenter.INSTANCE.sizeOf(workerRole);
        Random random = new Random(workerNum);
        return WorkersRefCenter.INSTANCE.find(workerRole, random.nextInt());
    }
}
