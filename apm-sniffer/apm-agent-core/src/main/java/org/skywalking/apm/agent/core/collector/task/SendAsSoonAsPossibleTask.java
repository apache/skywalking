package org.skywalking.apm.agent.core.collector.task;

import org.skywalking.apm.agent.core.collector.Sender;

/**
 * Created by xin on 2017/6/10.
 */
public abstract class SendAsSoonAsPossibleTask<V> extends CollectionDataSendTask<V> {
    private static final int DEFAULT_SLEEP_TIME = 500;
    private int sleepTime;

    public SendAsSoonAsPossibleTask(Sender<V> sender) {
        this(sender, DEFAULT_SLEEP_TIME);
    }

    public SendAsSoonAsPossibleTask(Sender<V> sender, int sleepTime) {
        super(sender);
        this.sleepTime = sleepTime;
    }

    @Override
    public void afterSend() {
        if (needSleep()) {
            try {
                Thread.sleep(sleepTime);
            } catch (InterruptedException e) {
                //TODO
            }
        }
    }

    public abstract boolean needSleep();
}
