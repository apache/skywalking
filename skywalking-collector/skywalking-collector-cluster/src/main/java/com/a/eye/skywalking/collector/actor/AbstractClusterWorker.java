package com.a.eye.skywalking.collector.actor;

import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import akka.cluster.Member;
import akka.cluster.MemberStatus;
import com.a.eye.skywalking.collector.cluster.WorkerListenerMessage;
import com.a.eye.skywalking.collector.cluster.WorkersListener;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * The <code>AbstractClusterWorker</code> should be implemented by any class whose instances
 * are intended to provide receive remote message that message will transfer across different jvm
 * which running in same server or another address server.
 * <p>
 * Usually the implemented class used to receive persistence data or aggregation the metric.
 *
 * @author pengys5
 * @since feature3.0
 */
public abstract class AbstractClusterWorker extends AbstractWorker {

    /**
     * Constructs a <code>AbstractClusterWorker</code> with the worker role and context.
     *
     * @param role           The responsibility of worker in cluster, more than one workers can have
     *                       same responsibility which use to provide load balancing ability.
     * @param clusterContext See {@link ClusterWorkerContext}
     * @param selfContext    See {@link LocalWorkerContext}
     */
    protected AbstractClusterWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    /**
     * Receive message
     *
     * @param message The persistence data or metric data.
     * @throws Exception The Exception happen in {@link #onWork(Object)}
     */
    final public void allocateJob(Object message) throws Exception {
        onWork(message);
    }

    /**
     * The data process logic in this method.
     *
     * @param message Cast the message object to a expect subclass.
     * @throws Exception Don't handle the exception, throw it.
     */
    protected abstract void onWork(Object message) throws Exception;

    static class WorkerWithAkka extends UntypedActor {
        private Logger logger = LogManager.getFormatterLogger(WorkerWithAkka.class);

        private Cluster cluster;
        private final AbstractClusterWorker ownerWorker;

        public WorkerWithAkka(AbstractClusterWorker ownerWorker) {
            this.ownerWorker = ownerWorker;
            cluster = Cluster.get(getContext().system());
        }

        @Override
        public void preStart() throws Exception {
            cluster.subscribe(getSelf(), ClusterEvent.MemberUp.class);
        }

        @Override
        public void postStop() throws Exception {
            cluster.unsubscribe(getSelf());
        }

        /**
         * Listening {@link ClusterEvent.MemberUp} and {@link ClusterEvent.CurrentClusterState}
         * cluster event, when event send from the member of {@link WorkersListener} then tell
         * the sender to register self.
         */
        @Override
        public void onReceive(Object message) throws Throwable {
            if (message instanceof ClusterEvent.CurrentClusterState) {
                ClusterEvent.CurrentClusterState state = (ClusterEvent.CurrentClusterState) message;
                for (Member member : state.getMembers()) {
                    if (member.status().equals(MemberStatus.up())) {
                        register(member);
                    }
                }
            } else if (message instanceof ClusterEvent.MemberUp) {
                ClusterEvent.MemberUp memberUp = (ClusterEvent.MemberUp) message;
                logger.info("receive ClusterEvent.MemberUp message, address: %s", memberUp.member().address().toString());
                register(memberUp.member());
            } else {
                logger.debug("worker class: %s, message class: %s", this.getClass().getName(), message.getClass().getName());
                ownerWorker.allocateJob(message);
            }
        }

        /**
         * When member role is {@link WorkersListener#WorkName} then Select actor from context
         * and send register message to {@link WorkersListener}
         *
         * @param member is the new created or restart worker
         */
        void register(Member member) {
            if (member.hasRole(WorkersListener.WorkName)) {
                WorkerListenerMessage.RegisterMessage registerMessage = new WorkerListenerMessage.RegisterMessage(ownerWorker.getRole());
                logger.info("member address: %s, worker path: %s", member.address().toString(), getSelf().path().toString());
                getContext().actorSelection(member.address() + "/user/" + WorkersListener.WorkName).tell(registerMessage, getSelf());
            }
        }
    }
}
