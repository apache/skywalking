package com.a.eye.skywalking.collector.worker.tracedag;

import com.a.eye.skywalking.collector.actor.*;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.Const;
import com.a.eye.skywalking.collector.worker.node.persistence.NodeCompLoad;
import com.a.eye.skywalking.collector.worker.node.persistence.NodeMappingSearchWithTimeSlice;
import com.a.eye.skywalking.collector.worker.noderef.persistence.NodeRefResSumSearchWithTimeSlice;
import com.a.eye.skywalking.collector.worker.noderef.persistence.NodeRefSearchWithTimeSlice;
import com.google.gson.JsonArray;
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
@PrepareForTest({ClusterWorkerContext.class, TraceDagGetWithTimeSlice.class})
@PowerMockIgnore({"javax.management.*"})
public class TraceDagGetWithTimeSliceTestCase {

    private TraceDagGetWithTimeSlice getObj;
    private ClusterWorkerContext clusterWorkerContext;

    @Before
    public void init() throws Exception {
        clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);

        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);

        WorkerRefs workerRefs_1 = mock(WorkerRefs.class);
        TraceDagGetAnswerGet_1 answer_1 = new TraceDagGetAnswerGet_1();
        doAnswer(answer_1).when(workerRefs_1).ask(Mockito.any(), Mockito.any(JsonObject.class));
        when(localWorkerContext.lookup(NodeCompLoad.WorkerRole.INSTANCE)).thenReturn(workerRefs_1);

        WorkerRefs workerRefs_2 = mock(WorkerRefs.class);
        TraceDagGetAnswerGet_2 answer_2 = new TraceDagGetAnswerGet_2();
        doAnswer(answer_2).when(workerRefs_2).ask(Mockito.any(NodeMappingSearchWithTimeSlice.RequestEntity.class), Mockito.any(JsonObject.class));
        when(localWorkerContext.lookup(NodeMappingSearchWithTimeSlice.WorkerRole.INSTANCE)).thenReturn(workerRefs_2);

        WorkerRefs workerRefs_3 = mock(WorkerRefs.class);
        TraceDagGetAnswerGet_3 answer_3 = new TraceDagGetAnswerGet_3();
        doAnswer(answer_3).when(workerRefs_3).ask(Mockito.any(NodeRefSearchWithTimeSlice.RequestEntity.class), Mockito.any(JsonObject.class));
        when(localWorkerContext.lookup(NodeRefSearchWithTimeSlice.WorkerRole.INSTANCE)).thenReturn(workerRefs_3);

        WorkerRefs workerRefs_4 = mock(WorkerRefs.class);
        TraceDagGetAnswerGet_4 answer_4 = new TraceDagGetAnswerGet_4();
        doAnswer(answer_4).when(workerRefs_4).ask(Mockito.any(NodeRefResSumSearchWithTimeSlice.RequestEntity.class), Mockito.any(JsonObject.class));
        when(localWorkerContext.lookup(NodeRefResSumSearchWithTimeSlice.WorkerRole.INSTANCE)).thenReturn(workerRefs_4);

        getObj = PowerMockito.spy(new TraceDagGetWithTimeSlice(TraceDagGetWithTimeSlice.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext));
    }

    @Test
    public void testRole() {
        Assert.assertEquals(TraceDagGetWithTimeSlice.class.getSimpleName(), TraceDagGetWithTimeSlice.WorkerRole.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), TraceDagGetWithTimeSlice.WorkerRole.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(TraceDagGetWithTimeSlice.class.getSimpleName(), TraceDagGetWithTimeSlice.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(TraceDagGetWithTimeSlice.class.getSimpleName(), TraceDagGetWithTimeSlice.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());
        Assert.assertEquals("/traceDag/timeSlice", TraceDagGetWithTimeSlice.Factory.INSTANCE.servletPath());
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        when(clusterWorkerContext.findProvider(NodeCompLoad.WorkerRole.INSTANCE)).thenReturn(NodeCompLoad.Factory.INSTANCE);
        when(clusterWorkerContext.findProvider(NodeMappingSearchWithTimeSlice.WorkerRole.INSTANCE)).thenReturn(NodeMappingSearchWithTimeSlice.Factory.INSTANCE);
        when(clusterWorkerContext.findProvider(NodeRefSearchWithTimeSlice.WorkerRole.INSTANCE)).thenReturn(NodeRefSearchWithTimeSlice.Factory.INSTANCE);
        when(clusterWorkerContext.findProvider(NodeRefResSumSearchWithTimeSlice.WorkerRole.INSTANCE)).thenReturn(NodeRefResSumSearchWithTimeSlice.Factory.INSTANCE);

        ArgumentCaptor<Role> argumentCaptor = ArgumentCaptor.forClass(Role.class);
        getObj.preStart();
        verify(clusterWorkerContext, times(4)).findProvider(argumentCaptor.capture());

        Assert.assertEquals("NodeCompLoad", argumentCaptor.getAllValues().get(0).roleName());
        Assert.assertEquals("NodeMappingSearchWithTimeSlice", argumentCaptor.getAllValues().get(1).roleName());
        Assert.assertEquals("NodeRefSearchWithTimeSlice", argumentCaptor.getAllValues().get(2).roleName());
        Assert.assertEquals("NodeRefResSumSearchWithTimeSlice", argumentCaptor.getAllValues().get(3).roleName());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnSearchError() throws Exception {
        Map<String, String[]> request = new HashMap<>();
        JsonObject response = new JsonObject();
        getObj.onSearch(request, response);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnSearchErrorStartTime() throws Exception {
        Map<String, String[]> request = createRequest();
        String[] startTime = {"xx"};
        request.put("startTime", startTime);

        JsonObject response = new JsonObject();
        getObj.onSearch(request, response);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOnSearchErrorEndTime() throws Exception {
        Map<String, String[]> request = createRequest();
        String[] endTime = {"xx"};
        request.put("endTime", endTime);

        JsonObject response = new JsonObject();
        getObj.onSearch(request, response);
    }

    private Map<String, String[]> createRequest() {
        Map<String, String[]> request = new HashMap<>();
        String[] startTime = {"10"};
        request.put("startTime", startTime);
        String[] endTime = {"20"};
        request.put("endTime", endTime);
        String[] timeSliceType = {"minute"};
        request.put("timeSliceType", timeSliceType);
        return request;
    }

    @Test
    public void testOnSearch() throws Exception {
        TraceDagDataBuilder builder = mock(TraceDagDataBuilder.class);
        PowerMockito.when(getObj, "getBuilder").thenReturn(builder);

        JsonObject response = new JsonObject();
        response.add(Const.RESULT, new JsonArray());
        PowerMockito.when(getObj, "getNewResponse").thenReturn(response);

        Map<String, String[]> request = createRequest();
        getObj.onSearch(request, response);
    }

    class TraceDagGetAnswerGet_1 implements Answer {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            return null;
        }
    }

    class TraceDagGetAnswerGet_2 implements Answer {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            NodeMappingSearchWithTimeSlice.RequestEntity entity = (NodeMappingSearchWithTimeSlice.RequestEntity) invocation.getArguments()[0];
            Assert.assertEquals(10, entity.getStartTime());
            Assert.assertEquals(20, entity.getEndTime());
            Assert.assertEquals("minute", entity.getSliceType());
            return null;
        }
    }

    class TraceDagGetAnswerGet_3 implements Answer {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            NodeRefSearchWithTimeSlice.RequestEntity entity = (NodeRefSearchWithTimeSlice.RequestEntity) invocation.getArguments()[0];
            Assert.assertEquals(10, entity.getStartTime());
            Assert.assertEquals(20, entity.getEndTime());
            Assert.assertEquals("minute", entity.getSliceType());
            return null;
        }
    }

    class TraceDagGetAnswerGet_4 implements Answer {
        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            NodeRefResSumSearchWithTimeSlice.RequestEntity entity = (NodeRefResSumSearchWithTimeSlice.RequestEntity) invocation.getArguments()[0];
            Assert.assertEquals(10, entity.getStartTime());
            Assert.assertEquals(20, entity.getEndTime());
            Assert.assertEquals("minute", entity.getSliceType());
            return null;
        }
    }
}
