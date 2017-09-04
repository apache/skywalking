package org.skywalking.apm.agent.core.remote;

/**
 * @author wusheng
 */
public class GRPCStreamServiceStatus {
    private volatile boolean status;

    public GRPCStreamServiceStatus(boolean status) {
        this.status = status;
    }

    public boolean isStatus() {
        return status;
    }

    public void finished() {
        this.status = true;
    }

    /**
     * @param maxTimeout max wait time, milliseconds.
     */
    public void wait4Finish(long maxTimeout) {
        long time = 0;
        while (!status) {
            if (time > maxTimeout) {
                break;
            }
            try2Sleep(5);
            time += 5;
        }
    }

    /**
     * Try to sleep, and ignore the {@link InterruptedException}
     *
     * @param millis the length of time to sleep in milliseconds
     */
    private void try2Sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {

        }
    }
}
