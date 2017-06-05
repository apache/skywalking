package org.skywalking.apm.agent.core.collector.task;

import java.util.Collection;
import org.skywalking.apm.agent.core.collector.Sender;

public abstract class CollectionDataSendTask<S> extends AbstractSendTask<S, Collection<? extends S>> {
    public CollectionDataSendTask(Sender<S> sender) {
        super(sender);
    }

    @Override
    protected void send(Sender<S> sender, Collection<? extends S> sendData) {
        for (S data : sendData) {
            try {
                sender.send(data);
            } catch (Exception e) {
                //
            }
        }
    }
}
