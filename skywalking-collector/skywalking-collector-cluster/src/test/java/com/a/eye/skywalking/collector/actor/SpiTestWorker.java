package com.a.eye.skywalking.collector.actor;

/**
 * @author pengys5
 */
public class SpiTestWorker extends AbstractWorker {

    public SpiTestWorker(String workerRole) {
        super(workerRole);
    }

    @Override
    public void receive(Object message) {
        if (message.equals("Test1")) {
            getSender().tell("Yes", getSelf());
        } else if (message.equals("Test2")) {
            getSender().tell("No", getSelf());
        }
    }
}
