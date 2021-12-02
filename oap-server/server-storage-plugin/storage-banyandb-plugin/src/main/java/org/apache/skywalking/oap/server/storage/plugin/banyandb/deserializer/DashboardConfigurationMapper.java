package org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.query.enumeration.TemplateType;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;

import java.util.List;

public class DashboardConfigurationMapper extends AbstractBanyanDBDeserializer<DashboardConfiguration> {
    public DashboardConfigurationMapper() {
        super(UITemplate.INDEX_NAME,
                ImmutableList.of(UITemplate.NAME, UITemplate.DISABLED),
                ImmutableList.of(UITemplate.ACTIVATED, UITemplate.CONFIGURATION, UITemplate.TYPE));
    }

    @Override
    public DashboardConfiguration map(RowEntity row) {
        DashboardConfiguration dashboardConfiguration = new DashboardConfiguration();
        final List<TagAndValue<?>> searchable = row.getTagFamilies().get(0);
        // name
        dashboardConfiguration.setName((String) searchable.get(0).getValue());
        // disabled
        dashboardConfiguration.setDisabled(BooleanUtils.valueToBoolean(((Number) searchable.get(1).getValue()).intValue()));
        final List<TagAndValue<?>> data = row.getTagFamilies().get(1);
        // activated
        dashboardConfiguration.setActivated(BooleanUtils.valueToBoolean(((Number) data.get(0).getValue()).intValue()));
        // configuration
        dashboardConfiguration.setConfiguration((String) data.get(1).getValue());
        // type
        dashboardConfiguration.setType(TemplateType.forName((String) data.get(2).getValue()));
        return dashboardConfiguration;
    }
}
