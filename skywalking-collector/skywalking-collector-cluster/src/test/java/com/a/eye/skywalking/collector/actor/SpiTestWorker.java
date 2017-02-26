package com.a.eye.skywalking.collector.actor;

import com.a.eye.skywalking.collector.actor.selector.RollingSelector;

/**
 * @author pengys5
 */
public class SpiTestWorker extends AbstractWorker {

    @Override
    public void receive(Object message) throws Throwable {
        if (message.equals("Test1")) {
            getSender().tell("Yes", getSelf());
        } else if (message.equals("Test2")) {
            getSender().tell("No", getSelf());
        } else if (message.equals("Test3")) {
            Object sendMessage = new Object();
            tell(new SpiTestWorkerFactory(), RollingSelector.INSTANCE, sendMessage);
        }
    }
}