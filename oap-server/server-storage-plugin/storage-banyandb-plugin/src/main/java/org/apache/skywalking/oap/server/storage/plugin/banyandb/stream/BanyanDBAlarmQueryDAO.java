package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.oap.server.core.alarm.AlarmRecord;
import org.apache.skywalking.oap.server.core.analysis.TimeBucket;
import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.type.AlarmMessage;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * {@link org.apache.skywalking.oap.server.core.alarm.AlarmRecord} is a stream,
 * which can be used to build a {@link org.apache.skywalking.oap.server.core.query.type.AlarmMessage}
 */
public class BanyanDBAlarmQueryDAO extends AbstractBanyanDBDAO implements IAlarmQueryDAO {
    public BanyanDBAlarmQueryDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public Alarms getAlarm(Integer scopeId, String keyword, int limit, int from, long startTB, long endTB, List<Tag> tags) throws IOException {
        List<AlarmMessage> messages = query(AlarmMessage.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
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
            }
        });

        Alarms alarms = new Alarms();
        alarms.setTotal(messages.size());
        alarms.getMsgs().addAll(messages);
        return alarms;
    }
}
