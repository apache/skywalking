package com.a.eye.skywalking.collector.worker.segment;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.ProviderNotFoundException;
import com.a.eye.skywalking.collector.actor.WorkerRefs;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentExceptionWithSegId;
import com.a.eye.skywalking.collector.worker.segment.persistence.SegmentTopSearchWithTimeSlice;
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
public class SegmentTopGetWithTimeSliceTestCase {

    private SegmentTopGetWithTimeSlice getObj;
    private SegmentTopGetAnswerGet answer;
    private ClusterWorkerContext clusterWorkerContext;

    @Before
    public void init() throws Exception {
        clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);

        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);

        answer = new SegmentTopGetAnswerGet();
        doAnswer(answer).when(workerRefs).ask(Mockito.any(SegmentTopSearchWithTimeSlice.RequestEntity.class), Mockito.any(JsonObject.class));

        when(localWorkerContext.lookup(SegmentTopSearchWithTimeSlice.WorkerRole.INSTANCE)).thenReturn(workerRefs);
        getObj = new SegmentTopGetWithTimeSlice(SegmentTopGetWithTimeSlice.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(SegmentTopGetWithTimeSlice.class.getSimpleName(), SegmentTopGetWithTimeSlice.WorkerRole.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), SegmentTopGetWithTimeSlice.WorkerRole.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(SegmentTopGetWithTimeSlice.class.getSimpleName(), SegmentTopGetWithTimeSlice.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(SegmentTopGetWithTimeSlice.class.getSimpleName(), SegmentTopGetWithTimeSlice.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());
        Assert.assertEquals("/segments/top/timeSlice", SegmentTopGetWithTimeSlice.Factory.INSTANCE.servletPath());
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        ClusterWorkerContext exceptionContext = PowerMockito.mock(ClusterWorkerContext.class);
        when(exceptionContext.findProvider(SegmentExceptionWithSegId.WorkerRole.INSTANCE)).thenReturn(SegmentExceptionWithSegId.Factory.INSTANCE);
        SegmentTopSearchWithTimeSlice.Factory.INSTANCE.setClusterContext(exceptionContext);

        when(clusterWorkerContext.findProvider(SegmentTopSearchWithTimeSlice.WorkerRole.INSTANCE)).thenReturn(SegmentTopSearchWithTimeSlice.Factory.INSTANCE);

        ArgumentCaptor<SegmentTopSearchWithTimeSlice.WorkerRole> argumentCaptor = ArgumentCaptor.forClass(SegmentTopSearchWithTimeSlice.WorkerRole.class);
        getObj.preStart();
        verify(clusterWorkerContext).findProvider(argumentCaptor.capture());
    }

    @Test
    public void testOnSearch() throws Exception {
        Map<String, String[]> request = createRequest();
        JsonObject response = new JsonObject();
        getObj.onSearch(request, response);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnSearchErrorStartTime() throws Exception {
        Map<String, String[]> request = createRequest();
        String[] startTime = {"x"};
        request.put("startTime", startTime);

        JsonObject response = new JsonObject();
        getObj.onSearch(request, response);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnSearchErrorEndTime() throws Exception {
        Map<String, String[]> request = createRequest();
        String[] endTime = {"x"};
        request.put("endTime", endTime);

        JsonObject response = new JsonObject();
        getObj.onSearch(request, response);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnSearchErrorFrom() throws Exception {
        Map<String, String[]> request = createRequest();
        String[] from = {"x"};
        request.put("from", from);

        JsonObject response = new JsonObject();
        getObj.onSearch(request, response);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnSearchErrorLimit() throws Exception {
        Map<String, String[]> request = createRequest();
        String[] limit = {"x"};
        request.put("limit", limit);

        JsonObject response = new JsonObject();
        getObj.onSearch(request, response);
    }

    private Map<String, String[]> createRequest(){
        Map<String, String[]> request = new HashMap<>();
        String[] startTime = {"10"};
        request.put("startTime", startTime);
        String[] endTime = {"20"};
        request.put("endTime", endTime);
        String[] from = {"30"};
        request.put("from", from);
        String[] limit = {"40"};
        request.put("limit", limit);
        String[] minCost = {"50"};
        request.put("minCost", minCost);
        String[] maxCost = {"60"};
        request.put("maxCost", maxCost);
        return request;
    }

    class SegmentTopGetAnswerGet implements Answer {

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            SegmentTopSearchWithTimeSlice.RequestEntity requestEntity = (SegmentTopSearchWithTimeSlice.RequestEntity) invocation.getArguments()[0];
            Assert.assertEquals(10, requestEntity.getStartTime());
            Assert.assertEquals(20, requestEntity.getEndTime());
            Assert.assertEquals(30, requestEntity.getFrom());
            Assert.assertEquals(40, requestEntity.getLimit());
            Assert.assertEquals(50, requestEntity.getMinCost());
            Assert.assertEquals(60, requestEntity.getMaxCost());
            return null;
        }
    }
}
