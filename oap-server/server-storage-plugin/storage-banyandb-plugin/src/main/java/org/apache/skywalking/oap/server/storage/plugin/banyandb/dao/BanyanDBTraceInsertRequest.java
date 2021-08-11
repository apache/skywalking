package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.skywalking.banyandb.client.request.TraceWriteRequest;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;

@RequiredArgsConstructor
public class BanyanDBTraceInsertRequest implements InsertRequest {

    @Getter
    private final TraceWriteRequest traceWriteRequest;
}
