package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.AlarmMessageMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.RowEntityMapper;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * {@link org.apache.skywalking.oap.server.core.alarm.AlarmRecord} is a stream,
 * which can be used to build a {@link org.apache.skywalking.oap.server.core.query.type.AlarmMessage}
 */
public class BanyanDBAlarmQueryDAO extends AbstractDAO<BanyanDBStorageClient> implements IAlarmQueryDAO {
    private final RowEntityMapper<AlarmMessage> mapper;

    public BanyanDBAlarmQueryDAO(BanyanDBStorageClient client) {
        super(client);
        mapper = new AlarmMessageMapper(this);
    }

    @Override
    public Alarms getAlarm(Integer scopeId, String keyword, int limit, int from, long startTB, long endTB, List<Tag> tags) throws IOException {
        final StreamQuery query = new StreamQuery(AlarmRecord.INDEX_NAME, mapper.searchableProjection());

        if (Objects.nonNull(scopeId)) {
            query.appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", AlarmRecord.SCOPE, (long) scopeId));
        }
        if (startTB != 0 && endTB != 0) {
            query.appendCondition(PairQueryCondition.LongQueryCondition.ge("searchable", AlarmRecord.START_TIME, TimeBucket.getTimestamp(startTB)));
            query.appendCondition(PairQueryCondition.LongQueryCondition.le("searchable", AlarmRecord.START_TIME, TimeBucket.getTimestamp(endTB)));
        }

        // TODO: support keyword search

        // TODO: support tag search

        query.setLimit(limit);
        query.setOffset(from);

        StreamQueryResponse resp = getClient().query(query);

        List<AlarmMessage> messages = resp.getElements().stream().map(mapper::map).collect(Collectors.toList());

        Alarms alarms = new Alarms();
        alarms.setTotal(messages.size());
        alarms.getMsgs().addAll(messages);
        return alarms;
    }
}
