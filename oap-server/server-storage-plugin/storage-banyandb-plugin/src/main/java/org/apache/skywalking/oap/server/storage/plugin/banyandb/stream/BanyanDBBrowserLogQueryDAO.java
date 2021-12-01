package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord;
import org.apache.skywalking.oap.server.core.browser.source.BrowserErrorCategory;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLog;
import org.apache.skywalking.oap.server.core.query.type.BrowserErrorLogs;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.query.IBrowserLogQueryDAO;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.BrowserErrorLogMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.RowEntityMapper;

import java.io.IOException;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link org.apache.skywalking.oap.server.core.browser.manual.errorlog.BrowserErrorLogRecord} is a stream
 */
public class BanyanDBBrowserLogQueryDAO extends AbstractDAO<BanyanDBStorageClient> implements IBrowserLogQueryDAO {
    private static final RowEntityMapper<BrowserErrorLog> MAPPER = new BrowserErrorLogMapper();

    public BanyanDBBrowserLogQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public BrowserErrorLogs queryBrowserErrorLogs(String serviceId, String serviceVersionId, String pagePathId, BrowserErrorCategory category, long startSecondTB, long endSecondTB, int limit, int from) throws IOException {
        final StreamQuery query = new StreamQuery(BrowserErrorLogRecord.INDEX_NAME, MAPPER.searchableProjection());
        query.setDataProjections(MAPPER.dataProjection());

        query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", BrowserErrorLogRecord.SERVICE_ID, serviceId));

        if (startSecondTB != 0 && endSecondTB != 0) {
            query.appendCondition(PairQueryCondition.LongQueryCondition.ge("searchable", BrowserErrorLogRecord.TIMESTAMP, TimeBucket.getTimestamp(startSecondTB)));
            query.appendCondition(PairQueryCondition.LongQueryCondition.le("searchable", BrowserErrorLogRecord.TIMESTAMP, TimeBucket.getTimestamp(endSecondTB)));
        }
        if (StringUtil.isNotEmpty(serviceVersionId)) {
            query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", BrowserErrorLogRecord.SERVICE_VERSION_ID, serviceVersionId));
        }
        if (StringUtil.isNotEmpty(pagePathId)) {
            query.appendCondition(PairQueryCondition.StringQueryCondition.eq("searchable", BrowserErrorLogRecord.PAGE_PATH_ID, pagePathId));
        }
        if (Objects.nonNull(category)) {
            query.appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", BrowserErrorLogRecord.ERROR_CATEGORY, (long) category.getValue()));
        }

        query.setOffset(from);
        query.setLimit(limit);

        final StreamQueryResponse resp = getClient().query(query);

        final BrowserErrorLogs logs = new BrowserErrorLogs();
        logs.getLogs().addAll(resp.getElements().stream().map(MAPPER::map).collect(Collectors.toList()));
        logs.setTotal(logs.getLogs().size());
        return logs;
    }
}
