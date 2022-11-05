package org.apache.skywalking.oap.server.core.storage.query;

import org.apache.skywalking.oap.server.core.analysis.manual.spanattach.SpanAttachedEventRecord;
import org.apache.skywalking.oap.server.library.module.Service;

import java.io.IOException;
import java.util.List;

public interface ISpanAttachedEventQueryDAO extends Service {
    List<SpanAttachedEventRecord> querySpanAttachedEvents(String traceId) throws IOException;
}
