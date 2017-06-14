package org.skywalking.apm.collector.worker.noderef;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;
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
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.WorkerRefs;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.worker.httpserver.ArgumentsParseException;
import org.skywalking.apm.collector.worker.noderef.persistence.NodeRefResSumGroupWithTimeSlice;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({ClusterWorkerContext.class})
@PowerMockIgnore({"javax.management.*"})
public class NodeRefResSumGetGroupWithTimeSliceTestCase {

    private NodeRefResSumGetGroupWithTimeSlice getObj;
    private NodeRefResSumGetAnswerGet answer;
    private ClusterWorkerContext clusterWorkerContext;

    @Before
    public void init() throws Exception {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        clusterWorkerContext = PowerMockito.mock(ClusterWorkerContext.class);

        LocalWorkerContext localWorkerContext = PowerMockito.mock(LocalWorkerContext.class);
        WorkerRefs workerRefs = mock(WorkerRefs.class);

        answer = new NodeRefResSumGetAnswerGet();
        doAnswer(answer).when(workerRefs).ask(Mockito.any(NodeRefResSumGroupWithTimeSlice.RequestEntity.class), Mockito.any(JsonObject.class));

        when(localWorkerContext.lookup(NodeRefResSumGroupWithTimeSlice.WorkerRole.INSTANCE)).thenReturn(workerRefs);
        getObj = new NodeRefResSumGetGroupWithTimeSlice(NodeRefResSumGetGroupWithTimeSlice.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(NodeRefResSumGetGroupWithTimeSlice.class.getSimpleName(), NodeRefResSumGetGroupWithTimeSlice.WorkerRole.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), NodeRefResSumGetGroupWithTimeSlice.WorkerRole.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        NodeRefResSumGetGroupWithTimeSlice.Factory factory = new NodeRefResSumGetGroupWithTimeSlice.Factory();
        Assert.assertEquals(NodeRefResSumGetGroupWithTimeSlice.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(NodeRefResSumGetGroupWithTimeSlice.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());
        Assert.assertEquals("/nodeRef/resSum/groupTimeSlice", factory.servletPath());
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        when(clusterWorkerContext.findProvider(NodeRefResSumGroupWithTimeSlice.WorkerRole.INSTANCE)).thenReturn(new NodeRefResSumGroupWithTimeSlice.Factory());

        ArgumentCaptor<NodeRefResSumGroupWithTimeSlice.WorkerRole> argumentCaptor = ArgumentCaptor.forClass(NodeRefResSumGroupWithTimeSlice.WorkerRole.class);
        getObj.preStart();
        verify(clusterWorkerContext).findProvider(argumentCaptor.capture());
    }

    @Test
    public void testOnReceive() throws Exception {
        Map<String, String[]> request = new HashMap<>();
        String[] startTime = {"100"};
        request.put("startTime", startTime);
        String[] endTime = {"200"};
        request.put("endTime", endTime);
        String[] timeSliceType = {"minute"};
        request.put("timeSliceType", timeSliceType);

        JsonObject response = new JsonObject();
        getObj.onReceive(request, response);
    }

    @Test(expected = ArgumentsParseException.class)
    public void testOnReceiveError() throws Exception {
        Map<String, String[]> request = new HashMap<>();
        JsonObject response = new JsonObject();
        getObj.onReceive(request, response);
    }

    @Test(expected = ArgumentsParseException.class)
    public void testOnReceiveStartTimeError() throws Exception {
        Map<String, String[]> request = new HashMap<>();
        String[] startTime = {"x"};
        request.put("startTime", startTime);
        String[] endTime = {"200"};
        request.put("endTime", endTime);
        String[] timeSliceType = {"minute"};
        request.put("timeSliceType", timeSliceType);
        JsonObject response = new JsonObject();

        getObj.onReceive(request, response);
    }

    @Test(expected = ArgumentsParseException.class)
    public void testOnReceiveEndTimeError() throws Exception {
        Map<String, String[]> request = new HashMap<>();
        String[] startTime = {"100"};
        request.put("startTime", startTime);
        String[] endTime = {"x"};
        request.put("endTime", endTime);
        String[] timeSliceType = {"minute"};
        request.put("timeSliceType", timeSliceType);
        JsonObject response = new JsonObject();
        getObj.onReceive(request, response);
    }

    class NodeRefResSumGetAnswerGet implements Answer {

        @Override
        public Object answer(InvocationOnMock invocation) throws Throwable {
            NodeRefResSumGroupWithTimeSlice.RequestEntity requestEntity = (NodeRefResSumGroupWithTimeSlice.RequestEntity)invocation.getArguments()[0];
            Assert.assertEquals(100L, requestEntity.getStartTime());
            Assert.assertEquals(200L, requestEntity.getEndTime());
            Assert.assertEquals("minute", requestEntity.getSliceType());
            return null;
        }
    }
}
