package com.a.eye.skywalking.storage.data.index;

import com.a.eye.skywalking.network.grpc.RequestSpan;
import com.a.eye.skywalking.network.grpc.TraceId;
import com.a.eye.skywalking.storage.data.file.DataFileNameDesc;
import com.a.eye.skywalking.storage.data.spandata.RequestSpanData;
import com.a.eye.skywalking.storage.util.NetUtils;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Created by wusheng on 2016/11/30.
 */
public class IndexOperatorTest {
    public static void main(String[] args) {
        try {
            TransportClient client = new PreBuiltTransportClient(Settings.EMPTY).addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName("test_es"), 9300));
            IndexOperator operator = new IndexOperator(client);

            for (int i = 0; i < 1_000_000_000; i++) {
                IndexMetaCollection collection = new IndexMetaCollection();
                for (int j = 0; j < 100; j++) {
                    RequestSpan requestSpan =
                            RequestSpan.newBuilder().setSpanType(1).setAddress(NetUtils.getLocalAddress().toString()).setApplicationCode("1").setCallType("1").setLevelId(0)
                                    .setProcessNo(19287).setStartDate(System.currentTimeMillis()).setTraceId(
                                    TraceId.newBuilder().addSegments(201611).addSegments(j).addSegments(8504828).addSegments(2277).addSegments(53).addSegments(3).build())
                                    .setUsername("1").setViewPointId("http://localhost:8080/wwww/test/helloWorld").setRouteKey(i).build();
                    IndexMetaInfo info = new IndexMetaInfo(new RequestSpanData(requestSpan), new DataFileNameDesc(), i, j);
                    collection.add(info);
                }

                operator.batchUpdate(collection);

                if (i % 100 == 0) {
                    System.out.println(" num=" + i + " ");
                }
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
    }
}
