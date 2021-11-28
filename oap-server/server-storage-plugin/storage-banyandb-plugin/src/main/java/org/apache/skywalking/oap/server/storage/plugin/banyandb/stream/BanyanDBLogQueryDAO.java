package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.enumeration.Order;
import org.apache.skywalking.oap.server.core.query.input.TraceScopeCondition;
import org.apache.skywalking.oap.server.core.query.type.Logs;
import org.apache.skywalking.oap.server.core.storage.query.ILogQueryDAO;

import java.io.IOException;
import java.util.List;

/**
 * {@link org.apache.skywalking.oap.server.core.analysis.manual.log.LogRecord} is a stream
 */
public class BanyanDBLogQueryDAO implements ILogQueryDAO {
    @Override
    public Logs queryLogs(String serviceId, String serviceInstanceId, String endpointId, TraceScopeCondition relatedTrace, Order queryOrder, int from, int limit, long startTB, long endTB, List<Tag> tags, List<String> keywordsOfContent, List<String> excludingKeywordsOfContent) throws IOException {
        return new Logs();
    }
}
