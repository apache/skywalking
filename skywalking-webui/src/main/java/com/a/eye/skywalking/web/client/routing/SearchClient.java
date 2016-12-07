package com.a.eye.skywalking.web.client.routing;

import com.a.eye.skywalking.network.grpc.QueryTask;
import com.a.eye.skywalking.network.grpc.SearchResult;
import com.a.eye.skywalking.network.grpc.TraceId;
import com.a.eye.skywalking.network.grpc.client.TraceSearchClient;
import com.a.eye.skywalking.web.dto.TraceNodeInfo;
import com.a.eye.skywalking.web.dto.TraceNodesResult;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by wusheng on 2016/12/6.
 */
public class SearchClient {
    private TraceSearchClient client;

    public SearchClient() {
        TraceSearchClient[] allSearchClients = RoutingServerWatcher.getAllSearchClient();
        if ((allSearchClients.length == 0)) {
            throw new RuntimeException("no active routing servers");
        }
        client = allSearchClients[ThreadLocalRandom.current().nextInt(0, allSearchClients.length)];
    }

    public TraceNodesResult searchSpan(String traceId){
        String[] traceIdSegments = traceId.split(".");
        TraceNodesResult traceNodesResult = new TraceNodesResult();
        if(traceIdSegments.length != 6){
            return traceNodesResult;
        }

        TraceId.Builder builder = TraceId.newBuilder();
        for (String traceIdSegment : traceIdSegments) {
            builder.addSegments(Long.parseLong(traceIdSegment));
        }

        QueryTask.Builder queryTaskBuilder = QueryTask.newBuilder().setTraceId(builder.build());
        SearchResult result = client.search(queryTaskBuilder.build());

        List<TraceNodeInfo> traceNodes = traceNodesResult.getResult();
        result.getSpansList().forEach((span -> {
            traceNodes.add(new TraceNodeInfo((span)));
        }));
        return traceNodesResult;
    }
}
