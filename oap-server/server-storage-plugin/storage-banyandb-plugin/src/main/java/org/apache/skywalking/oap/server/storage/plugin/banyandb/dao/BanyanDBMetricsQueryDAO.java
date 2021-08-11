package org.apache.skywalking.oap.server.storage.plugin.banyandb.dao;

import org.apache.skywalking.oap.server.core.query.input.Duration;
import org.apache.skywalking.oap.server.core.query.input.MetricsCondition;
import org.apache.skywalking.oap.server.core.query.type.HeatMap;
import org.apache.skywalking.oap.server.core.query.type.MetricsValues;
import org.apache.skywalking.oap.server.core.storage.query.IMetricsQueryDAO;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

public class BanyanDBMetricsQueryDAO implements IMetricsQueryDAO {
    @Override
    public long readMetricsValue(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        return 0L;
    }

    @Override
    public MetricsValues readMetricsValues(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        return new MetricsValues();
    }

    @Override
    public List<MetricsValues> readLabeledMetricsValues(MetricsCondition condition, String valueColumnName, List<String> labels, Duration duration) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public HeatMap readHeatMap(MetricsCondition condition, String valueColumnName, Duration duration) throws IOException {
        return new HeatMap();
    }
}
