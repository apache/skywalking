package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.oap.server.core.storage.query.IZipkinQueryDAO;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

//TODO: Not support BanyanDB for query yet.
public class BanyanDBZipkinQueryDAO implements IZipkinQueryDAO {
    @Override
    public List<String> getServiceNames(final long startTimeMillis, final long endTimeMillis) throws IOException {
        return new ArrayList<>();
    }

    @Override
    public List<String> getRemoteServiceNames(final long startTimeMillis,
                                              final long endTimeMillis,
                                              final String serviceName) throws IOException {
        return new ArrayList<>();
    }

    @Override
    public List<String> getSpanNames(final long startTimeMillis,
                                     final long endTimeMillis,
                                     final String serviceName) throws IOException {
        return new ArrayList<>();
    }

    @Override
    public List<Span> getTrace(final String traceId) throws IOException {
        return new ArrayList<>();
    }

    @Override
    public List<List<Span>> getTraces(final QueryRequest request) throws IOException {
        return new ArrayList<>();
    }

    @Override
    public List<List<Span>> getTraces(final Set<String> traceIds) throws IOException {
        return new ArrayList<>();
    }
}
