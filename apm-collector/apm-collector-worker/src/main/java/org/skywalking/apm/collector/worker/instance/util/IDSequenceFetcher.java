package org.skywalking.apm.collector.worker.instance.util;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public enum IDSequenceFetcher {
    INSTANCE;

    private ScheduledFuture task;
    private Logger logger = LogManager.getLogger(IDSequenceFetcher.class);
    private ESLock esLock = new ESLock();

    public void fetchSequence(Listener listener) {
        for (int index = 0; index < 3; index++) {
            boolean updateSuccess = esLock.tryLock((start, end) -> {
                listener.success(new IDSequence(start, end));
            });

            if (updateSuccess) {
                return;
            } else {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                }
            }
        }

        listener.failed();
    }

    public void fetchSequenceInBackGround(final IDSequenceCache cache) {
        if (task != null) {
            logger.info("Fetch sequence task already running.");
            return;
        }

        task = Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> fetchSequence(new Listener() {
            @Override
            public void failed() {
                logger.warn("Failed to fetch id sequence.");
            }

            @Override
            public void success(IDSequence idSequence) {
                cache.state = IDSequenceCache.State.NORMAL;
                cache.idSequence = idSequence;
                if (task != null) {
                    task.cancel(false);
                    task = null;
                }
            }
        }), 0, 500, TimeUnit.MILLISECONDS);
    }

    interface Listener {

        void failed();

        void success(IDSequence idSequence);
    }
}
