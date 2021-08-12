package org.apache.skywalking.banyandb.client;

import org.apache.skywalking.banyandb.client.request.TraceFetchRequest;
import org.apache.skywalking.banyandb.client.request.TraceSearchRequest;
import org.apache.skywalking.banyandb.client.request.TraceWriteRequest;
import org.apache.skywalking.banyandb.client.response.BanyanDBQueryResponse;

import java.util.List;

public interface BanyanDBTraceService {
    // read/query
    BanyanDBQueryResponse queryBasicTraces(TraceSearchRequest request);

    BanyanDBQueryResponse queryByTraceId(TraceFetchRequest traceFetchRequest);

    // write
    void writeEntity(List<TraceWriteRequest> data);
}
