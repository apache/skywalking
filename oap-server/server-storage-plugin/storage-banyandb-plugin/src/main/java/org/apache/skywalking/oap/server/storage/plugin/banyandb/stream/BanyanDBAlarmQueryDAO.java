package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;

import java.io.IOException;
import java.util.List;

/**
 * {@link org.apache.skywalking.oap.server.core.alarm.AlarmRecord} is a stream,
 * which can be used to build a {@link org.apache.skywalking.oap.server.core.query.type.AlarmMessage}
 */
public class BanyanDBAlarmQueryDAO implements IAlarmQueryDAO {
    @Override
    public Alarms getAlarm(Integer scopeId, String keyword, int limit, int from, long startTB, long endTB, List<Tag> tags) throws IOException {
        return new Alarms();
    }
}
