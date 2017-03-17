package com.a.eye.skywalking.collector.cluster;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import com.a.eye.skywalking.logging.ILog;
import com.a.eye.skywalking.logging.LogManager;
import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.ClusterWorkerRef;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * <code>WorkersListener</code> listening the register message from workers
 * implementation of the {@link com.a.eye.skywalking.collector.actor.AbstractWorker}
 * and terminated message from akka cluster.
 * <p>
 * when listened register message then begin to watch the state for this worker
 * and register to {@link ClusterWorkerContext} and {@link #relation}.
 * <p>
 * when listened terminate message then unregister from {@link ClusterWorkerContext} and {@link #relation} .
 *
 * @author pengys5
 */
public class WorkersListener extends UntypedActor {

    private ILog logger = LogManager.getLogger(WorkersListener.class);

    public static final String WorkName = "WorkersListener";

    private Cluster cluster = Cluster.get(getContext().system());

    private Map<ActorRef, ClusterWorkerRef> relation = new ConcurrentHashMap<>();

    private final ClusterWorkerContext clusterContext;

    public WorkersListener(ClusterWorkerContext clusterContext) {
        this.clusterContext = clusterContext;
    }

    @Override
    public void preStart() throws Exception {
        cluster.subscribe(getSelf(), ClusterEvent.UnreachableMember.class);
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof WorkerListenerMessage.RegisterMessage) {
            WorkerListenerMessage.RegisterMessage register = (WorkerListenerMessage.RegisterMessage) message;
            ActorRef sender = getSender();
//            logger.info("register worker of role: %s, path: %s", register.getWorkRole(), sender.toString());
            ClusterWorkerRef workerRef = new ClusterWorkerRef(sender, register.getRole());
            relation.put(sender, workerRef);
            clusterContext.put(new ClusterWorkerRef(sender, register.getRole()));
        } else if (message instanceof Terminated) {
            Terminated terminated = (Terminated) message;
            clusterContext.remove(relation.get(terminated.getActor()));
            relation.remove(terminated.getActor());
        } else if (message instanceof ClusterEvent.UnreachableMember) {
            ClusterEvent.UnreachableMember unreachableMember = (ClusterEvent.UnreachableMember) message;

            Iterator<Map.Entry<ActorRef, ClusterWorkerRef>> iterator = relation.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<ActorRef, ClusterWorkerRef> next = iterator.next();

                if (next.getKey().path().address().equals(unreachableMember.member().address())) {
                    clusterContext.remove(next.getValue());
                    iterator.remove();
                }
            }
        } else {
            unhandled(message);
        }
    }
}
