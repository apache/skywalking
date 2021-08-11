package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.oap.server.core.analysis.manual.searchtag.Tag;
import org.apache.skywalking.oap.server.core.query.type.Alarms;
import org.apache.skywalking.oap.server.core.storage.query.IAlarmQueryDAO;

import java.io.IOException;
import java.util.List;

public class BanyanDBAlarmQueryDAO implements IAlarmQueryDAO {
    @Override
    public Alarms getAlarm(Integer scopeId, String keyword, int limit, int from, long startTB, long endTB, List<Tag> tags) throws IOException {
        return new Alarms();
    }
}
