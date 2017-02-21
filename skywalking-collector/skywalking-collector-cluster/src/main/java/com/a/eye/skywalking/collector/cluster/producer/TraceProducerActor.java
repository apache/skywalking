package com.a.eye.skywalking.collector.cluster.producer;

import com.a.eye.skywalking.collector.cluster.Const;
import com.a.eye.skywalking.collector.cluster.manager.ActorRefCenter;
import com.a.eye.skywalking.collector.cluster.message.TraceMessages.JobFailed;
import com.a.eye.skywalking.collector.cluster.message.TraceMessages.TransformationJob;
import akka.actor.UntypedActor;
import org.springframework.context.annotation.Scope;

//#frontend
//@Named("TraceProducerActor")
@Scope("prototype")
public class TraceProducerActor extends UntypedActor {

    int jobCounter = 0;

    @Override
    public void onReceive(Object message) {
        int actorSize = ActorRefCenter.INSTANCE.sizeOf(Const.Trace_Consumer_Role);
        if (actorSize == 0) {
            System.out.println("actorList null");
        } else {
            System.out.println("sizeOf: " + actorSize);
        }

        if ((message instanceof TransformationJob) && actorSize == 0) {
            TransformationJob job = (TransformationJob)message;
            getSender().tell(new JobFailed("Service unavailable, try again later", job), getSender());
        } else if (message instanceof TransformationJob) {
            TransformationJob job = (TransformationJob)message;
            jobCounter++;
            ActorRefCenter.INSTANCE.find(Const.Trace_Consumer_Role,
                (candidates) -> candidates.get(jobCounter % candidates.size()));
        } else {
            unhandled(message);
        }
    }

}
//#frontend
