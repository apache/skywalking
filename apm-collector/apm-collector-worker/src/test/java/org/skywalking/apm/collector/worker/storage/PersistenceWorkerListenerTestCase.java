package org.skywalking.apm.collector.worker.storage;

import org.junit.Assert;
import org.junit.Test;
import org.mockito.Mockito;
import org.skywalking.apm.collector.actor.AbstractLocalSyncWorker;

import java.util.List;

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
