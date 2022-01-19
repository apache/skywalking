/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.apache.skywalking.oap.server.storage.plugin.banyandb.stream;

import com.google.common.collect.ImmutableList;
import org.apache.skywalking.banyandb.v1.client.RowEntity;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamQueryResponse;
import org.apache.skywalking.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.banyandb.v1.client.Tag;
import org.apache.skywalking.banyandb.v1.client.TagAndValue;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.query.enumeration.TemplateType;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * {@link org.apache.skywalking.oap.server.core.management.ui.template.UITemplate} is a stream
 */
public class BanyanDBUITemplateManagementDAO extends AbstractBanyanDBDAO implements UITemplateManagementDAO {
    public BanyanDBUITemplateManagementDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<DashboardConfiguration> getAllTemplates(Boolean includingDisabled) throws IOException {
        StreamQueryResponse resp = query(UITemplate.INDEX_NAME, ImmutableList.of(UITemplate.NAME, UITemplate.DISABLED),
                new QueryBuilder() {
                    @Override
                    public void apply(StreamQuery query) {
                        query.setDataProjections(ImmutableList.of(UITemplate.ACTIVATED, UITemplate.CONFIGURATION, UITemplate.TYPE));
                        query.setLimit(10000);
                        if (!includingDisabled) {
                            query.appendCondition(eq(UITemplate.DISABLED, BooleanUtils.FALSE));
                        }
                    }
                });

        return resp.getElements().stream().map(new DashboardConfigurationDeserializer()).collect(Collectors.toList());
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
                .timestamp(Instant.now().toEpochMilli())
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

    public static class DashboardConfigurationDeserializer implements RowEntityDeserializer<DashboardConfiguration> {
        @Override
        public DashboardConfiguration apply(RowEntity row) {
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
}
