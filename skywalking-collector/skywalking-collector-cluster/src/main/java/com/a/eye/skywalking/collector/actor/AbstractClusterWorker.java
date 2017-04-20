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
 * The <code>AbstractClusterWorker</code> implementations represent workers,
 * which receive remote messages.
 * <p>
 * Usually, the implementations are doing persistent, or aggregate works.
 *
 * @author pengys5
 * @since v3.0-2017
 */
public abstract class AbstractClusterWorker extends AbstractWorker {

    /**
     * Construct an <code>AbstractClusterWorker</code> with the worker role and context.
     *
     * @param role If multi-workers are for load balance, they should be more likely called worker instance. Meaning,
     * each worker have multi instances.
     * @param clusterContext See {@link ClusterWorkerContext}
     * @param selfContext See {@link LocalWorkerContext}
     */
    protected AbstractClusterWorker(Role role, ClusterWorkerContext clusterContext, LocalWorkerContext selfContext) {
        super(role, clusterContext, selfContext);
    }

    /**
     * This method use for message producer to call for send message.
     *
     * @param message The persistence data or metric data.
     * @throws Exception The Exception happen in {@link #onWork(Object)}
     */
    final public void allocateJob(Object message) throws Exception {
        onWork(message);
    }

    /**
     * This method use for message receiver to analyse message.
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
                ClusterEvent.CurrentClusterState state = (ClusterEvent.CurrentClusterState)message;
                for (Member member : state.getMembers()) {
                    if (member.status().equals(MemberStatus.up())) {
                        register(member);
                    }
                }
            } else if (message instanceof ClusterEvent.MemberUp) {
                ClusterEvent.MemberUp memberUp = (ClusterEvent.MemberUp)message;
                logger.info("receive ClusterEvent.MemberUp message, address: %s", memberUp.member().address().toString());
                register(memberUp.member());
            } else {
                logger.debug("worker class: %s, message class: %s", this.getClass().getName(), message.getClass().getName());
                ownerWorker.allocateJob(message);
            }
        }

        /**
         * When member role is {@link WorkersListener#WORK_NAME} then Select actor from context
         * and send register message to {@link WorkersListener}
         *
         * @param member is the new created or restart worker
         */
        void register(Member member) {
            if (member.hasRole(WorkersListener.WORK_NAME)) {
                WorkerListenerMessage.RegisterMessage registerMessage = new WorkerListenerMessage.RegisterMessage(ownerWorker.getRole());
                logger.info("member address: %s, worker path: %s", member.address().toString(), getSelf().path().toString());
                getContext().actorSelection(member.address() + "/user/" + WorkersListener.WORK_NAME).tell(registerMessage, getSelf());
            }
        }
    }
}
