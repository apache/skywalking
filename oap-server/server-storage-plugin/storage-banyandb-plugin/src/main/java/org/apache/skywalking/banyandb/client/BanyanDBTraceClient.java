package org.apache.skywalking.banyandb.client;

import org.apache.skywalking.banyandb.client.request.TraceSearchRequest;
import org.apache.skywalking.banyandb.client.response.BanyanDBQueryResponse;

import java.util.concurrent.Future;

public interface BanyanDBTraceClient {
    // read/query
    BanyanDBQueryResponse queryBasicTraces(TraceSearchRequest request);

    BanyanDBQueryResponse queryByTraceId(String traceId);

    // write
    Future<Boolean> writeEntity();
}
