package org.apache.skywalking.oap.server.storage.plugin.banyandb.converter;

import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;

import java.util.List;

public class DashboardConfigurationMapper implements RowEntityMapper<DashboardConfiguration> {
    @Override
    public DashboardConfiguration map(RowEntity row) {
        DashboardConfiguration dashboardConfiguration = new DashboardConfiguration();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        dashboardConfiguration.setName((String) searchable.get(0).getValue());
        dashboardConfiguration.setDisabled(BooleanUtils.valueToBoolean(((Number) searchable.get(1).getValue()).intValue()));
        // TODO: convert back from data?
        return dashboardConfiguration;
    }
}
