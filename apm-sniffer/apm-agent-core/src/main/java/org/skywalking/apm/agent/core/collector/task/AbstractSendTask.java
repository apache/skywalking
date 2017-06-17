package org.skywalking.apm.agent.core.collector.task;

import org.skywalking.apm.agent.core.collector.Sender;
import org.skywalking.apm.agent.core.collector.Task;
import org.skywalking.apm.logging.ILog;
import org.skywalking.apm.logging.LogManager;

public abstract class AbstractSendTask<S, D> implements Task {

    private ILog logger = LogManager.getLogger(AbstractSendTask.class);
    private boolean stop;
    private Sender<S> sender;

    public AbstractSendTask(Sender<S> sender) {
        this.sender = sender;
        stop = false;
    }

    @Override
    public void start() {
        Thread thread = new Thread(new Runnable() {
            public void run() {
                while (!stop) {
                    try {
                        send(sender, sendData());
                    } catch (Throwable e) {
                        logger.error("Failed to send data", e);
                    }
                    afterSend();
                }
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    protected abstract void send(Sender<S> sender, D t) throws Exception;

    public abstract void afterSend();

    protected abstract D sendData();

    @Override
    public void stop() {
        stop = true;
    }
}
