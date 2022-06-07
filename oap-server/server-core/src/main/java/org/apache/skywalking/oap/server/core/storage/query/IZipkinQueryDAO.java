package org.apache.skywalking.oap.server.core.storage.query;

import java.io.IOException;
import java.util.List;
import java.util.Set;
import org.apache.skywalking.oap.server.core.storage.DAO;
import zipkin2.Span;
import zipkin2.storage.QueryRequest;

public interface IZipkinQueryDAO extends DAO {
    List<String> getServiceNames(final long startTimeMillis, final long endTimeMillis) throws IOException;

    List<String> getRemoteServiceNames(final long startTimeMillis, final long endTimeMillis, final String serviceName) throws IOException;

    List<String> getSpanNames(final long startTimeMillis, final long endTimeMillis, final String serviceName) throws IOException;

    List<Span> getTrace(final String traceId) throws IOException;

    List<List<Span>> getTraces(final QueryRequest request) throws IOException;

    List<List<Span>> getTraces(final Set<String> traceIds) throws IOException;
}
