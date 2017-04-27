package org.skywalking.apm.collector.worker.httpserver;

import com.google.gson.JsonObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {TestAbstractPost.class})
@PowerMockIgnore( {"javax.management.*"})
public class AbstractPostTestCase {

    private TestAbstractPost post;

    @Before
    public void init() {
        ClusterWorkerContext clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);
        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);
        post = spy(new TestAbstractPost(TestAbstractPost.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext));
    }

    @Test
    public void testOnWork() throws Exception {
        String request = "testOnWork";
        post.onWork(request);
        verify(post).onReceive(anyString());
    }

    @Test
    public void testOnWorkError() throws Exception {
        post.onWork(new JsonObject());
        PowerMockito.verifyPrivate(post).invoke("saveException", any(IllegalArgumentException.class));
    }
}
