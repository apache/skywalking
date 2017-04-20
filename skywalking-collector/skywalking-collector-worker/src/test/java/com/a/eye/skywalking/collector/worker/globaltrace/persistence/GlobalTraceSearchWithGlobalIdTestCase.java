package com.a.eye.skywalking.collector.worker.globaltrace.persistence;

import com.a.eye.skywalking.collector.actor.ClusterWorkerContext;
import com.a.eye.skywalking.collector.actor.LocalWorkerContext;
import com.a.eye.skywalking.collector.actor.selector.RollingSelector;
import com.a.eye.skywalking.collector.worker.globaltrace.GlobalTraceIndex;
import com.a.eye.skywalking.collector.worker.segment.SegmentIndex;
import com.a.eye.skywalking.collector.worker.storage.GetResponseFromEs;
import com.google.gson.JsonObject;
import org.elasticsearch.action.get.GetResponse;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import java.util.TimeZone;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({GetResponseFromEs.class})
@PowerMockIgnore({"javax.management.*"})
public class GlobalTraceSearchWithGlobalIdTestCase {

    private GetResponseFromEs getResponseFromEs;

    private String global_Str = "{\"subSegIds\":\"Segment.1491277162066.18986177.70531.27.1\"}";
    private String seg_str = "{\"ts\":\"Segment.1491277162066.18986177.70531.27.1\",\"st\":1491277162066,\"et\":1491277165743,\"ss\":[{\"si\":0,\"ps\":-1,\"st\":1491277162141,\"et\":1491277162144,\"on\":\"Jedis/getClient\",\"ts\":{\"span.layer\":\"db\",\"component\":\"Redis\",\"db.type\":\"Redis\",\"peer.host\":\"127.0.0.1\",\"span.kind\":\"client\"},\"tb\":{},\"ti\":{\"peer.PORT\":6379},\"lo\":[]}],\"ac\":\"cache-service\",\"gt\":[\"Trace.1491277147443.-1562443425.70539.65.2\"],\"sampled\":true,\"minute\":201704041139,\"hour\":201704041100,\"day\":201704040000,\"aggId\":null}";

    @Before
    public void init() {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        getResponseFromEs = PowerMockito.mock(GetResponseFromEs.class);
        Whitebox.setInternalState(GetResponseFromEs.class, "INSTANCE", getResponseFromEs);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(GlobalTraceSearchWithGlobalId.class.getSimpleName(), GlobalTraceSearchWithGlobalId.WorkerRole.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), GlobalTraceSearchWithGlobalId.WorkerRole.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        Assert.assertEquals(GlobalTraceSearchWithGlobalId.class.getSimpleName(), GlobalTraceSearchWithGlobalId.Factory.INSTANCE.role().roleName());
        Assert.assertEquals(GlobalTraceSearchWithGlobalId.class.getSimpleName(), GlobalTraceSearchWithGlobalId.Factory.INSTANCE.workerInstance(null).getClass().getSimpleName());
    }

    @Test
    public void testOnWork() throws Exception {
        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        GlobalTraceSearchWithGlobalId globalTraceSearchWithGlobalId = new GlobalTraceSearchWithGlobalId(GlobalTraceSearchWithGlobalId.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext);

        GetResponse getResponse = mock(GetResponse.class);
        when(getResponseFromEs.get(GlobalTraceIndex.INDEX, GlobalTraceIndex.TYPE_RECORD, "Trace.1491277147443.-1562443425.70539.65.2")).thenReturn(getResponse);
        when(getResponse.getSourceAsString()).thenReturn(global_Str);

        GetResponse segResponse = mock(GetResponse.class);
        when(getResponseFromEs.get(SegmentIndex.INDEX, SegmentIndex.TYPE_RECORD, "Segment.1491277162066.18986177.70531.27.1")).thenReturn(segResponse);
        when(segResponse.getSourceAsString()).thenReturn(seg_str);

        JsonObject response = new JsonObject();
        globalTraceSearchWithGlobalId.onWork("Trace.1491277147443.-1562443425.70539.65.2", response);
    }
}
