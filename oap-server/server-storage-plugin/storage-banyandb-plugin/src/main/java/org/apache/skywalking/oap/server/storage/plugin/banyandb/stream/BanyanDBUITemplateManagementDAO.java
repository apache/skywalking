package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/**
 * {@link org.apache.skywalking.oap.server.core.management.ui.template.UITemplate} is a stream
 */
public class BanyanDBUITemplateManagementDAO implements UITemplateManagementDAO {
    @Override
    public List<DashboardConfiguration> getAllTemplates(Boolean includingDisabled) throws IOException {
        return Collections.emptyList();
    }

    @Override
    public TemplateChangeStatus addTemplate(DashboardSetting setting) throws IOException {
        return TemplateChangeStatus.builder().status(false).message("Can't add a new template").build();
    }

    @Override
    public TemplateChangeStatus changeTemplate(DashboardSetting setting) throws IOException {
        return TemplateChangeStatus.builder().status(false).message("Can't add/update the template").build();
    }

    @Override
    public TemplateChangeStatus disableTemplate(String name) throws IOException {
        return TemplateChangeStatus.builder().status(false).message("Can't add/update the template").build();
    }
}
