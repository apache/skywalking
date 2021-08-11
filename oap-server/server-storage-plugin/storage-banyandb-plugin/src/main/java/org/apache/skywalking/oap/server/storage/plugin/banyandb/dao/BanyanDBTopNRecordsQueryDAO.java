package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.query.ITopNRecordsQueryDAO;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class BanyanDBTopNRecordsQueryDAO implements ITopNRecordsQueryDAO {
    @Override
    public List<SelectedRecord> readSampledRecords(TopNCondition condition, String valueColumnName, Duration duration) throws IOException {
        return Collections.emptyList();
    }
}
