package com.a.eye.skywalking.collector.worker.globaltrace;

import com.a.eye.skywalking.collector.worker.globaltrace.persistence.GlobalTraceSearchWithGlobalId;
import com.a.eye.skywalking.collector.worker.segment.SegmentIndex;
import com.a.eye.skywalking.collector.worker.storage.EsClient;
import com.a.eye.skywalking.collector.worker.tools.JsonFileReader;
import com.google.gson.JsonObject;
import org.elasticsearch.action.get.GetRequestBuilder;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.client.Client;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import static org.powermock.api.mockito.PowerMockito.*;

/**
 * @author pengys5
 */
@RunWith(PowerMockRunner.class)
@PrepareForTest({EsClient.class})
@PowerMockIgnore("javax.management.*")
public class GlobalTraceSearchTestCase {

    @Test
    public void testSearchWithGlobalId() throws Exception {
        Client client = mock(Client.class);
        mockStatic(EsClient.class);
//        when(EsClient.INSTANCE.getClient()).thenReturn(client);

        String globalTraceId = "Global.1";
        String segment_1 = "SEGMENT.1";
        String segment_2 = "SEGMENT.2";

//        String globalData = JsonFileReader.INSTANCE.read(this.getClass().getResource("/").getPath() + "/json/globaltrace/global.json");
//        mockSegment(client, GlobalTraceIndex.Index, GlobalTraceIndex.Type_Record, globalTraceId, globalData);
//
//        String segment_1_Data = JsonFileReader.INSTANCE.read(this.getClass().getResource("/").getPath() + "/json/globaltrace/segment_1.json");
//        mockSegment(client, SegmentIndex.Index, SegmentIndex.Type_Record, segment_1, segment_1_Data);
//
//        String segment_2_Data = JsonFileReader.INSTANCE.read(this.getClass().getResource("/").getPath() + "/json/globaltrace/segment_2.json");
//        mockSegment(client, SegmentIndex.Index, SegmentIndex.Type_Record, segment_2, segment_2_Data);
//
//        GlobalTraceSearchWithGlobalId search = new GlobalTraceSearchWithGlobalId(null, null, null);
//
//        JsonObject responseObj = new JsonObject();
//        search.allocateJob(globalTraceId, responseObj);
//        System.out.println(responseObj);
    }

    private void mockSegment(Client client, String index, String type, String segmentId, String source) {
        GetRequestBuilder builder = mock(GetRequestBuilder.class);
        when(client.prepareGet(index, type, segmentId)).thenReturn(builder);
        GetResponse response = mock(GetResponse.class);
        when(builder.get()).thenReturn(response);
        when(response.getSourceAsString()).thenReturn(source);
    }
}
