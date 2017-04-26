package com.a.eye.skywalking.collector.worker.storage;

import com.a.eye.skywalking.collector.actor.AbstractLocalSyncWorker;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;

/**
 * @author pengys5
 */
public class PersistenceWorkerListenerTestCase {

    @Test
    public void register() {
        PersistenceWorkerListener.INSTANCE.reset();
        AbstractLocalSyncWorker worker = Mockito.mock(AbstractLocalSyncWorker.class);
        PersistenceWorkerListener.INSTANCE.register(worker);

        List<AbstractLocalSyncWorker> workers = PersistenceWorkerListener.INSTANCE.getWorkers();
        Assert.assertEquals(worker, workers.get(0));
    }
}
