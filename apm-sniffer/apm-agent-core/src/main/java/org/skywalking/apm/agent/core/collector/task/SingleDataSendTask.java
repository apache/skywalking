package org.skywalking.apm.agent.core.collector.task;

import org.skywalking.apm.agent.core.collector.Sender;

public abstract class SingleDataSendTask<S> extends AbstractSendTask<S, S> {
    public SingleDataSendTask(Sender<S> sender) {
        super(sender);
    }

    @Override
    protected void send(Sender<S> sender, S data) throws Exception {
        if (data != null) {
            sender.send(data);
        }
    }
}
