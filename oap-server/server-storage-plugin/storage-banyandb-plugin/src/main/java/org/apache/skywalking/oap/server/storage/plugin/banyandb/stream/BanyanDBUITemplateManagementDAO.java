package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.banyandb.v1.client.Tag;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.DashboardConfigurationMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.deserializer.RowEntityMapper;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link org.apache.skywalking.oap.server.core.management.ui.template.UITemplate} is a stream
 */
public class BanyanDBUITemplateManagementDAO extends AbstractDAO<BanyanDBStorageClient> implements UITemplateManagementDAO {
    private static final RowEntityMapper<DashboardConfiguration> MAPPER = new DashboardConfigurationMapper();

    private static final long UI_TEMPLATE_TIMESTAMP = 1L;

    public BanyanDBUITemplateManagementDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<DashboardConfiguration> getAllTemplates(Boolean includingDisabled) throws IOException {
        StreamQuery query = new StreamQuery(UITemplate.INDEX_NAME, MAPPER.dataProjection());
        query.setLimit(10000);
        if (!includingDisabled) {
            query.appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", UITemplate.DISABLED, (long) BooleanUtils.FALSE));
        }
        query.setDataProjections(MAPPER.dataProjection());
        StreamQueryResponse resp = this.getClient().query(query);
        return resp.getElements().stream().map(MAPPER::map).collect(Collectors.toList());
    }

    @Override
    public TemplateChangeStatus addTemplate(DashboardSetting setting) throws IOException {
        final UITemplate uiTemplate = setting.toEntity();

        StreamWrite request = StreamWrite.builder()
                .name(UITemplate.INDEX_NAME)
                // searchable - name
                .searchableTag(Tag.stringField(uiTemplate.getName()))
                // searchable - disabled
                .searchableTag(Tag.longField(uiTemplate.getDisabled()))
                // data - type
                .dataTag(Tag.stringField(uiTemplate.getType()))
                // data - configuration
                .dataTag(Tag.stringField(uiTemplate.getConfiguration()))
                // data - activated
                .dataTag(Tag.longField(uiTemplate.getActivated()))
                .timestamp(UI_TEMPLATE_TIMESTAMP)
                .elementId(uiTemplate.id())
                .build();
        getClient().write(request);
        return TemplateChangeStatus.builder().status(true).build();
    }

    @Override
    public TemplateChangeStatus changeTemplate(DashboardSetting setting) throws IOException {
        return TemplateChangeStatus.builder().status(false).message("Can't update the template").build();
    }

    @Override
    public TemplateChangeStatus disableTemplate(String name) throws IOException {
        return TemplateChangeStatus.builder().status(false).message("Can't disable the template").build();
    }
}
