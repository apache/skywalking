package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.TopNCondition;
import org.apache.skywalking.oap.server.core.query.type.KeyValue;
import org.apache.skywalking.oap.server.core.query.type.SelectedRecord;
import org.apache.skywalking.oap.server.core.storage.query.IAggregationQueryDAO;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class BanyanDBAggregationQueryDAO implements IAggregationQueryDAO {
    @Override
    public List<SelectedRecord> sortMetrics(TopNCondition condition, String valueColumnName, Duration duration, List<KeyValue> additionalConditions) throws IOException {
        return Collections.emptyList();
    }
}
