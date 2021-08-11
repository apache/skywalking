package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.oap.server.core.storage.IBatchDAO;
import org.apache.skywalking.oap.server.library.client.request.InsertRequest;
import org.apache.skywalking.oap.server.library.client.request.PrepareRequest;

import java.util.List;

public class BanyanDBBatchDAO implements IBatchDAO {
    @Override
    public void insert(InsertRequest insertRequest) {

    }

    @Override
    public void flush(List<PrepareRequest> prepareRequests) {

    }
}
