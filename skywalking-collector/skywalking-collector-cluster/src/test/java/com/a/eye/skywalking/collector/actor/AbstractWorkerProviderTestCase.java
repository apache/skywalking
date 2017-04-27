package com.a.eye.skywalking.collector.actor;

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.powermock.api.mockito.PowerMockito.*;

import org.mockito.Mockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({AbstractWorker.class})
@PowerMockIgnore({"javax.management.*"})
public class AbstractWorkerProviderTestCase {

    @Test(expected = IllegalArgumentException.class)
    public void testNullWorkerInstanceCreate() throws ProviderNotFoundException {
        AbstractWorkerProvider provider = mock(AbstractWorkerProvider.class);
        when(provider.workerInstance(null)).thenReturn(null);

        AbstractWorker worker = mock(AbstractWorker.class);
        provider.create(worker);
    }

    @Test
    public void testNoneWorkerOwner() throws ProviderNotFoundException {
        AbstractWorkerProvider provider = mock(AbstractWorkerProvider.class);

        ClusterWorkerContext context = mock(ClusterWorkerContext.class);
        provider.setClusterContext(context);

        AbstractWorker worker = mock(AbstractWorker.class);
        when(provider.workerInstance(context)).thenReturn(worker);

        provider.create(null);
        Mockito.verify(provider).onCreate(null);
    }

    @Test
    public void testHasWorkerOwner() throws ProviderNotFoundException {
        AbstractWorkerProvider provider = mock(AbstractWorkerProvider.class);

        ClusterWorkerContext context = mock(ClusterWorkerContext.class);
        provider.setClusterContext(context);

        AbstractWorker worker = mock(AbstractWorker.class);
        when(provider.workerInstance(context)).thenReturn(worker);

        AbstractWorker workerOwner = mock(AbstractWorker.class);
        LocalWorkerContext localWorkerContext = mock(LocalWorkerContext.class);
        when(workerOwner.getSelfContext()).thenReturn(localWorkerContext);

        provider.create(workerOwner);
        Mockito.verify(provider).onCreate(localWorkerContext);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHasWorkerOwnerButNoneContext() throws ProviderNotFoundException {
        AbstractWorkerProvider provider = mock(AbstractWorkerProvider.class);

        ClusterWorkerContext context = mock(ClusterWorkerContext.class);
        provider.setClusterContext(context);

        AbstractWorker worker = mock(AbstractWorker.class);
        when(provider.workerInstance(context)).thenReturn(worker);

        AbstractWorker workerOwner = mock(AbstractWorker.class);
        when(workerOwner.getSelfContext()).thenReturn(null);

        provider.create(workerOwner);
    }
}
