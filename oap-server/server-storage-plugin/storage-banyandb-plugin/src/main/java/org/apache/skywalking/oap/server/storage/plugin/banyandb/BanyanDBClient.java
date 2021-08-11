package org.apache.skywalking.oap.server.storage.plugin.banyandb;

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.client.BanyanDBService;
import org.apache.skywalking.banyandb.client.request.TraceSearchRequest;
import org.apache.skywalking.banyandb.client.request.TraceWriteRequest;
import org.apache.skywalking.banyandb.client.response.BanyanDBQueryResponse;
import org.apache.skywalking.oap.server.library.client.Client;

import java.io.IOException;
import java.util.List;

@RequiredArgsConstructor
public class BanyanDBClient implements Client, BanyanDBService {
    private final BanyanDBService delegation;

    @Override
    public void connect() throws Exception {

    }

    @Override
    public void shutdown() throws IOException {

    }

    @Override
    public BanyanDBQueryResponse queryBasicTraces(TraceSearchRequest request) {
        return delegation.queryBasicTraces(request);
    }

    @Override
    public BanyanDBQueryResponse queryByTraceId(String traceId) {
        return delegation.queryByTraceId(traceId);
    }

    @Override
    public void writeEntity(List<TraceWriteRequest> data) {
        delegation.writeEntity(data);
    }
}
