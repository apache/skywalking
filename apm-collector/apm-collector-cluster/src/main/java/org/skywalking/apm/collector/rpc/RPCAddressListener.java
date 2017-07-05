package org.skywalking.apm.collector.rpc;

import akka.actor.ActorRef;
import akka.actor.Terminated;
import akka.actor.UntypedActor;
import akka.cluster.Cluster;
import akka.cluster.ClusterEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;

/**
 * @author pengys5
 */
public class RPCAddressListener extends UntypedActor {

    private final Logger logger = LogManager.getFormatterLogger(RPCAddressListener.class);

    public static final String WORK_NAME = "RPCAddressListener";

    private final ClusterWorkerContext clusterContext;
    private Cluster cluster = Cluster.get(getContext().system());

    public RPCAddressListener(ClusterWorkerContext clusterContext) {
        this.clusterContext = clusterContext;
    }

    @Override
    public void preStart() throws Exception {
        cluster.subscribe(getSelf(), ClusterEvent.UnreachableMember.class);
    }

    @Override
    public void onReceive(Object message) throws Throwable {
        if (message instanceof RPCAddressListenerMessage.ConfigMessage) {
            RPCAddressListenerMessage.ConfigMessage configMessage = (RPCAddressListenerMessage.ConfigMessage)message;
            ActorRef sender = getSender();
            logger.info("address: %s, port: %s", configMessage.getConfig().getAddress(), configMessage.getConfig().getPort());
            String ownerAddress = sender.path().address().hostPort();
            clusterContext.getRpcContext().putAddress(ownerAddress, configMessage.getConfig());
        } else if (message instanceof Terminated) {
            Terminated terminated = (Terminated)message;
            clusterContext.getRpcContext().removeAddress(terminated.getActor().path().address().hostPort());
        } else if (message instanceof ClusterEvent.UnreachableMember) {
            ClusterEvent.UnreachableMember unreachableMember = (ClusterEvent.UnreachableMember)message;
            clusterContext.getRpcContext().removeAddress(unreachableMember.member().address().hostPort());
        } else {
            unhandled(message);
        }
    }
}
