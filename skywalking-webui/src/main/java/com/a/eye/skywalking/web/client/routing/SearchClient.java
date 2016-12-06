package com.a.eye.skywalking.web.client.routing;

import com.a.eye.skywalking.network.grpc.client.TraceSearchClient;
import com.a.eye.skywalking.web.dto.TraceNodesResult;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by wusheng on 2016/12/6.
 */
public class SearchClient {
    private TraceSearchClient client;

    public SearchClient() {
        TraceSearchClient[] allSearchClients = RoutingServerWatcher.getAllSearchClient();

        client = allSearchClients[ThreadLocalRandom.current().nextInt(0, allSearchClients.length)];
    }

    public TraceNodesResult searchSpan(String traceId){
        String[] traceIdSegments = traceId.split(".");
        if(traceIdSegments.length != 6){
            return new TraceNodesResult();
        }
        return new TraceNodesResult();
    }
}
