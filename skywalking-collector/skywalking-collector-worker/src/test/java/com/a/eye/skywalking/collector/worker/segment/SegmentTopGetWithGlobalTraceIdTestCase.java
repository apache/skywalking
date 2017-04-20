package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentExceptionWithSegId;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentTopSearchWithGlobalTraceId;
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
import java.util.TimeZone;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ClusterWorkerContext.class})
@PowerMockIgnore({"javax.management.*"})
public class SegmentTopGetWithGlobalTraceIdTestCase {

    private SegmentTopGetWithGlobalTraceId getObj;
    private SegmentTopGetAnswerGet answer;
    private ClusterWorkerContext clusterWorkerContext;

    @Before
    public void init() throws Exception {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);

        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);

        answer = new SegmentTopGetAnswerGet();
        doAnswer(answer).when(workerRefs).ask(Mockito.any(SegmentTopSearchWithGlobalTraceId.RequestEntity.class), Mockito.any(JsonObject.class));

        when(localWorkerContext.lookup(SegmentTopSearchWithGlobalTraceId.WorkerRole.INSTANCE)).thenReturn(workerRefs);
        getObj = new SegmentTopGetWithGlobalTraceId(SegmentTopGetWithGlobalTraceId.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(SegmentTopGetWithGlobalTraceId.class.getSimpleName(), SegmentTopGetWithGlobalTraceId.WorkerRole.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), SegmentTopGetWithGlobalTraceId.WorkerRole.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(SegmentTopGetWithGlobalTraceId.class.getSimpleName(), SegmentTopGetWithGlobalTraceId.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(SegmentTopGetWithGlobalTraceId.class.getSimpleName(), SegmentTopGetWithGlobalTraceId.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());
        Assert.assertEquals("/segments/top/globalTraceId", SegmentTopGetWithGlobalTraceId.Factory.INSTANCE.servletPath());
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        ClusterWorkerContext exceptionContext = PowerMockito.mock(ClusterWorkerContext.class);
        when(exceptionContext.findProvider(SegmentExceptionWithSegId.WorkerRole.INSTANCE)).thenReturn(SegmentExceptionWithSegId.Factory.INSTANCE);
        SegmentTopSearchWithGlobalTraceId.Factory.INSTANCE.setClusterContext(exceptionContext);

        when(clusterWorkerContext.findProvider(SegmentTopSearchWithGlobalTraceId.WorkerRole.INSTANCE)).thenReturn(SegmentTopSearchWithGlobalTraceId.Factory.INSTANCE);

        ArgumentCaptor<SegmentTopSearchWithGlobalTraceId.WorkerRole> argumentCaptor = ArgumentCaptor.forClass(SegmentTopSearchWithGlobalTraceId.WorkerRole.class);
        getObj.preStart();
        verify(clusterWorkerContext).findProvider(argumentCaptor.capture());
    }

    @Test
    public void testOnSearch() throws Exception {
        Map<String, String[]> request = new HashMap<>();
        String[] globalTraceId = {"TestId"};
        request.put("globalTraceId", globalTraceId);
        String[] from = {"20"};
        request.put("from", from);
        String[] limit = {"50"};
        request.put("limit", limit);

        JsonObject response = new JsonObject();
        getObj.onSearch(request, response);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnSearchError() throws Exception {
        Map<String, String[]> request = new HashMap<>();
        JsonObject response = new JsonObject();
        getObj.onSearch(request, response);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnSearchErrorFrom() throws Exception {
        Map<String, String[]> request = new HashMap<>();
        String[] globalTraceId = {"TestId"};
        request.put("globalTraceId", globalTraceId);
        String[] from = {"x"};
        request.put("from", from);
        String[] limit = {"50"};
        request.put("limit", limit);

        JsonObject response = new JsonObject();
        getObj.onSearch(request, response);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnSearchErrorLimit() throws Exception {
        Map<String, String[]> request = new HashMap<>();
        String[] globalTraceId = {"TestId"};
        request.put("globalTraceId", globalTraceId);
        String[] from = {"20"};
        request.put("from", from);
        String[] limit = {"x"};
        request.put("limit", limit);

        JsonObject response = new JsonObject();
        getObj.onSearch(request, response);
    }

    class SegmentTopGetAnswerGet implements Answer {

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            SegmentTopSearchWithGlobalTraceId.RequestEntity requestEntity = (SegmentTopSearchWithGlobalTraceId.RequestEntity)invocation.getArguments()[0];
            Assert.assertEquals("TestId", requestEntity.getGlobalTraceId());
            Assert.assertEquals(20, requestEntity.getFrom());
            Assert.assertEquals(50, requestEntity.getLimit());
            return null;
        }
    }
}
