package com.a.eye.skywalking.collector.worker.globaltrace;

import com.a.eye.skywalking.collector.worker.globaltrace.persistence.GlobalTraceSearchWithGlobalId;
import com.a.eye.skywalking.collector.worker.segment.SegmentIndex;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.storage.GetResponseFromEs;
import com.a.eye.skywalking.collector.worker.tools.JsonFileReader;
import com.google.gson.JsonObject;
import org.elasticsearch.action.get.GetResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({EsClient.class, GetResponseFromEs.class})
@PowerMockIgnore("javax.management.*")
public class GlobalTraceSearchTestCase {

    @Test
    public void testSearchWithGlobalId() throws Exception {
        String globalTraceId = "Global.1";
        String segment_1 = "Segment.1490922929258.927784221.5991.27.1";

        GetResponseFromEs getResponseFromEs = PowerMockito.mock(GetResponseFromEs.class);
        Whitebox.setInternalState(GetResponseFromEs.class, "INSTANCE", getResponseFromEs);

        String globalData = JsonFileReader.INSTANCE.read(this.getClass().getResource("/").getPath() + "/json/globaltrace/persistence/globaltrace_search.json");
        mockSegment(getResponseFromEs, GlobalTraceIndex.INDEX, GlobalTraceIndex.TYPE_RECORD, globalTraceId, globalData);

        String segment_Data = JsonFileReader.INSTANCE.read(this.getClass().getResource("/").getPath() + "/json/globaltrace/persistence/globaltrace_segment.json");
        mockSegment(getResponseFromEs, SegmentIndex.INDEX, SegmentIndex.TYPE_RECORD, segment_1, segment_Data);

        GlobalTraceSearchWithGlobalId search = new GlobalTraceSearchWithGlobalId(GlobalTraceSearchWithGlobalId.WorkerRole.INSTANCE, null, null);

        JsonObject responseObj = new JsonObject();
//        search.allocateJob(globalTraceId, responseObj);
//        System.out.println(responseObj);
    }

    private void mockSegment(GetResponseFromEs getResponseFromEs, String index, String type, String id, String source) {
        GetResponse getResponse = mock(GetResponse.class);
        when(getResponseFromEs.get(index, type, id)).thenReturn(getResponse);
        when(getResponse.getSourceAsString()).thenReturn(source);
    }
}
