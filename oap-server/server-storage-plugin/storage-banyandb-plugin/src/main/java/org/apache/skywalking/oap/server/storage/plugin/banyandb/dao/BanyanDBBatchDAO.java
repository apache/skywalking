package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.banyandb.client.request.TraceWriteRequest;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBClient;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class BanyanDBBatchDAO extends AbstractDAO<BanyanDBClient> implements IBatchDAO {
    public BanyanDBBatchDAO(BanyanDBClient client) {
        super(client);
    }

    @Override
    public void insert(InsertRequest insertRequest) {
        getClient().writeEntity(Collections.singletonList(((BanyanDBTraceInsertRequest) insertRequest).getTraceWriteRequest()));
    }

    @Override
    public void flush(List<PrepareRequest> prepareRequests) {
        List<TraceWriteRequest> requests = prepareRequests.stream().map(prepareRequest -> (TraceWriteRequest) prepareRequest).collect(Collectors.toList());
        getClient().writeEntity(requests);
    }
}
