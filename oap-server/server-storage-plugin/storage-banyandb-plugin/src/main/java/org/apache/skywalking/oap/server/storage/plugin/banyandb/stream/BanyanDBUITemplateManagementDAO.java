package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.storage.AbstractDAO;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.converter.DashboardConfigurationMapper;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.converter.RowEntityMapper;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link org.apache.skywalking.oap.server.core.management.ui.template.UITemplate} is a stream
 */
public class BanyanDBUITemplateManagementDAO extends AbstractDAO<BanyanDBStorageClient> implements UITemplateManagementDAO {
    private static final RowEntityMapper<DashboardConfiguration> MAPPER = new DashboardConfigurationMapper();

    public BanyanDBUITemplateManagementDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<DashboardConfiguration> getAllTemplates(Boolean includingDisabled) throws IOException {
        StreamQuery query = new StreamQuery(UITemplate.INDEX_NAME, ImmutableList.of(
                UITemplate.NAME,
                UITemplate.DISABLED
        ));
        query.setLimit(10000);
        if (!includingDisabled) {
            query.appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", UITemplate.DISABLED, (long) BooleanUtils.FALSE));
        }
        StreamQueryResponse resp = this.getClient().query(query);
        return resp.getElements().stream().map(MAPPER::map).collect(Collectors.toList());
    }

    @Override
    public TemplateChangeStatus addTemplate(DashboardSetting setting) throws IOException {
        // TODO: support single write
    }

    @Override
    public TemplateChangeStatus changeTemplate(DashboardSetting setting) throws IOException {
        return TemplateChangeStatus.builder().status(false).message("Can't update the template").build();
    }

    @Override
    public TemplateChangeStatus disableTemplate(String name) throws IOException {
        return TemplateChangeStatus.builder().status(false).message("Can't add/update the template").build();
    }
}
