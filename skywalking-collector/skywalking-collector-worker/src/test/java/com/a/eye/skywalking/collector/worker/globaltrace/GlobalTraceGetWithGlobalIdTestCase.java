package com.a.eye.skywalking.collector.worker.globaltrace;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.globaltrace.persistence.GlobalTraceSearchWithGlobalId;
import com.google.gson.JsonObject;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ClusterWorkerContext.class})
@PowerMockIgnore({"javax.management.*"})
public class GlobalTraceGetWithGlobalIdTestCase {

    private GlobalTraceGetWithGlobalId getObj;
    private GlobalTraceAnswerGet answer;
    private ClusterWorkerContext clusterWorkerContext;

    @Before
    public void init() throws Exception {
        clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);

        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);

        answer = new GlobalTraceAnswerGet();
        doAnswer(answer).when(workerRefs).ask(Mockito.anyString(), Mockito.any(JsonObject.class));

        when(localWorkerContext.lookup(GlobalTraceSearchWithGlobalId.WorkerRole.INSTANCE)).thenReturn(workerRefs);
        getObj = new GlobalTraceGetWithGlobalId(GlobalTraceGetWithGlobalId.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(GlobalTraceGetWithGlobalId.class.getSimpleName(), GlobalTraceGetWithGlobalId.WorkerRole.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), GlobalTraceGetWithGlobalId.WorkerRole.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(GlobalTraceGetWithGlobalId.class.getSimpleName(), GlobalTraceGetWithGlobalId.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(GlobalTraceGetWithGlobalId.class.getSimpleName(), GlobalTraceGetWithGlobalId.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());
        Assert.assertEquals("/globalTrace/globalId", GlobalTraceGetWithGlobalId.Factory.INSTANCE.servletPath());
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        when(clusterWorkerContext.findProvider(GlobalTraceSearchWithGlobalId.WorkerRole.INSTANCE)).thenReturn(GlobalTraceSearchWithGlobalId.Factory.INSTANCE);

        ArgumentCaptor<GlobalTraceSearchWithGlobalId.WorkerRole> argumentCaptor = ArgumentCaptor.forClass(GlobalTraceSearchWithGlobalId.WorkerRole.class);
        getObj.preStart();
        verify(clusterWorkerContext).findProvider(argumentCaptor.capture());
    }

    @Test
    public void testOnSearch() throws Exception {
        Map<String, String[]> request = new HashMap<>();
        String[] globalId = {"Test"};
        request.put("globalId", globalId);

        JsonObject response = new JsonObject();
        getObj.onSearch(request, response);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnSearchError() throws Exception {
        Map<String, String[]> request = new HashMap<>();
        JsonObject response = new JsonObject();
        getObj.onSearch(request, response);
    }

    class GlobalTraceAnswerGet implements Answer {

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            String globalId = (String) invocation.getArguments()[0];
            System.out.println(globalId);
            Assert.assertEquals("Test", globalId);
            return null;
        }
    }
}
