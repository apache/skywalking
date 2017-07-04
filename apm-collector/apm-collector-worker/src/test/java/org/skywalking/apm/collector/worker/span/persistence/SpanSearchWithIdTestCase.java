package org.skywalking.apm.collector.worker.span.persistence;

import com.google.gson.JsonObject;
import java.util.TimeZone;
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
import org.skywalking.apm.collector.actor.ClusterWorkerContext;
import org.skywalking.apm.collector.actor.LocalWorkerContext;
import org.skywalking.apm.collector.actor.selector.RollingSelector;
import org.skywalking.apm.collector.worker.Const;
import org.skywalking.apm.collector.worker.segment.SegmentIndex;
import org.skywalking.apm.collector.worker.segment.mock.SegmentMock;
import org.skywalking.apm.collector.worker.storage.GetResponseFromEs;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({GetResponseFromEs.class})
@PowerMockIgnore({"javax.management.*"})
public class SpanSearchWithIdTestCase {

    private GetResponseFromEs getResponseFromEs;

    @Before
    public void init() {
        System.setProperty("user.timezone", "UTC");
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        getResponseFromEs = PowerMockito.mock(GetResponseFromEs.class);
        Whitebox.setInternalState(GetResponseFromEs.class, "INSTANCE", getResponseFromEs);
    }

    @Test
    public void testRole() {
        Assert.assertEquals(SpanSearchWithId.class.getSimpleName(), SpanSearchWithId.WorkerRole.INSTANCE.roleName());
        Assert.assertEquals(RollingSelector.class.getSimpleName(), SpanSearchWithId.WorkerRole.INSTANCE.workerSelector().getClass().getSimpleName());
    }

    @Test
    public void testFactory() {
        SpanSearchWithId.Factory factory = new SpanSearchWithId.Factory();
        Assert.assertEquals(SpanSearchWithId.class.getSimpleName(), factory.role().roleName());
        Assert.assertEquals(SpanSearchWithId.class.getSimpleName(), factory.workerInstance(null).getClass().getSimpleName());
    }

    @Test
    public void testOnWork() throws Exception {
        ClusterWorkerContext clusterWorkerContext = new ClusterWorkerContext(null);
        LocalWorkerContext localWorkerContext = new LocalWorkerContext();
        SpanSearchWithId spanSearchWithId = new SpanSearchWithId(SpanSearchWithId.WorkerRole.INSTANCE, clusterWorkerContext, localWorkerContext);

        SegmentMock mock = new SegmentMock();
        String sourceString = mock.loadJsonFile("/json/span/persistence/segment.json");
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponseFromEs.get(SegmentIndex.INDEX, SegmentIndex.TYPE_RECORD, "1")).thenReturn(getResponse);
        when(getResponse.getSourceAsString()).thenReturn(sourceString);

        SpanSearchWithId.RequestEntity request = new SpanSearchWithId.RequestEntity("1", "0");
        JsonObject response = new JsonObject();
        spanSearchWithId.onWork(request, response);

        JsonObject spanJsonObj = response.get(Const.RESULT).getAsJsonObject();
        String value = spanJsonObj.get("on").getAsString();
        Assert.assertEquals("/portal/", value);
    }

}
