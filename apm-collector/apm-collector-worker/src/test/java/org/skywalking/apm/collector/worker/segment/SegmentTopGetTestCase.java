package org.skywalking.apm.collector.worker.segment;

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
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.ProviderNotFoundException;
import org.skywalking.apm.collector.actor.WorkerRefs;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.worker.httpserver.ArgumentsParseException;
import org.skywalking.apm.collector.worker.segment.persistence.SegmentExceptionWithSegId;
import org.skywalking.apm.collector.worker.segment.persistence.SegmentTopSearch;

import java.util.HashMap;
import java.util.Map;
import java.util.TimeZone;

import static org.mockito.Mockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest( {ClusterWorkerContext.class})
@PowerMockIgnore( {"javax.management.*"})
public class SegmentTopGetTestCase {

    private SegmentTopGet getObj;
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
        doAnswer(answer).when(workerRefs).ask(Mockito.any(SegmentTopSearch.RequestEntity.class), Mockito.any(JsonObject.class));

        when(localWorkerContext.lookup(SegmentTopSearch.WorkerRole.INSTANCE)).thenReturn(workerRefs);
        getObj = new SegmentTopGet(SegmentTopGet.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(SegmentTopGet.class.getSimpleName(), SegmentTopGet.WorkerRole.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), SegmentTopGet.WorkerRole.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        SegmentTopGet.Factory factory = new SegmentTopGet.Factory();
        Assert.assertEquals(SegmentTopGet.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(SegmentTopGet.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());
        Assert.assertEquals("/segments/top/timeSlice", factory.servletPath());
    }

    @Test
    public void testPreStart() throws ProviderNotFoundException {
        ClusterWorkerContext exceptionContext = PowerMockito.mock(ClusterWorkerContext.class);
        SegmentExceptionWithSegId.Factory factory = new SegmentExceptionWithSegId.Factory();
        when(exceptionContext.findProvider(SegmentExceptionWithSegId.WorkerRole.INSTANCE)).thenReturn(factory);

        SegmentTopSearch.Factory factory1 = new SegmentTopSearch.Factory();
        factory1.setClusterContext(exceptionContext);

        when(clusterWorkerContext.findProvider(SegmentTopSearch.WorkerRole.INSTANCE)).thenReturn(factory1);

        ArgumentCaptor<SegmentTopSearch.WorkerRole> argumentCaptor = ArgumentCaptor.forClass(SegmentTopSearch.WorkerRole.class);
        getObj.preStart();
        verify(clusterWorkerContext).findProvider(argumentCaptor.capture());
    }

    @Test
    public void testOnSearch() throws Exception {
        Map<String, String[]> request = createRequest();
        JsonObject response = new JsonObject();
        getObj.onReceive(request, response);
    }

    @Test(expected = ArgumentsParseException.class)
    public void testOnSearchErrorStartTime() throws Exception {
        Map<String, String[]> request = createRequest();
        String[] startTime = {"x"};
        request.put("startTime", startTime);

        JsonObject response = new JsonObject();
        getObj.onReceive(request, response);
    }

    @Test(expected = ArgumentsParseException.class)
    public void testOnSearchErrorEndTime() throws Exception {
        Map<String, String[]> request = createRequest();
        String[] endTime = {"x"};
        request.put("endTime", endTime);

        JsonObject response = new JsonObject();
        getObj.onReceive(request, response);
    }

    @Test(expected = ArgumentsParseException.class)
    public void testOnSearchErrorFrom() throws Exception {
        Map<String, String[]> request = createRequest();
        String[] from = {"x"};
        request.put("from", from);

        JsonObject response = new JsonObject();
        getObj.onReceive(request, response);
    }

    @Test(expected = ArgumentsParseException.class)
    public void testOnSearchErrorLimit() throws Exception {
        Map<String, String[]> request = createRequest();
        String[] limit = {"x"};
        request.put("limit", limit);

        JsonObject response = new JsonObject();
        getObj.onReceive(request, response);
    }

    private Map<String, String[]> createRequest() {
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
            SegmentTopSearch.RequestEntity requestEntity = (SegmentTopSearch.RequestEntity) invocation.getArguments()[0];
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
