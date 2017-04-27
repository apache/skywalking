package org.skywalking.apm.collector.queue;

import java.util.concurrent.ThreadFactory;

/**
 * @author pengys5
 */
public enum DaemonThreadFactory implements ThreadFactory {
    INSTANCE;

    @Override
    public Thread newThread(Runnable r) {
        Thread t = new Thread(r);
        t.setDaemon(true);
        return t;
    }
}
