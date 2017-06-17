package org.skywalking.apm.agent.core.collector.task;

import org.skywalking.apm.agent.core.collector.Sender;

public abstract class SendAtFixedTimeTask<T> extends SingleDataSendTask<T> {

    private int sleepTime;

    public SendAtFixedTimeTask(Sender<T> sender, int sleepTime) {
        super(sender);
        this.sleepTime = sleepTime;
    }

    @Override
    public void afterSend() {
        try {
            Thread.sleep(sleepTime);
        } catch (InterruptedException e) {
            //TODO
        }
    }

}
