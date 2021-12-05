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

import org.apache.skywalking.banyandb.v1.client.PairQueryCondition;
import org.apache.skywalking.banyandb.v1.client.StreamQuery;
import org.apache.skywalking.banyandb.v1.client.StreamWrite;
import org.apache.skywalking.banyandb.v1.client.Tag;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.BanyanDBStorageClient;
import org.apache.skywalking.oap.server.storage.plugin.banyandb.schema.UITemplateBuilder;

import java.io.IOException;
import java.util.List;

/**
 * {@link org.apache.skywalking.oap.server.core.management.ui.template.UITemplate} is a stream
 */
public class BanyanDBUITemplateManagementDAO extends AbstractBanyanDBDAO implements UITemplateManagementDAO {
    public BanyanDBUITemplateManagementDAO(BanyanDBStorageClient client) {
        super(client);
    }

    @Override
    public List<DashboardConfiguration> getAllTemplates(Boolean includingDisabled) throws IOException {
        return query(DashboardConfiguration.class, new QueryBuilder() {
            @Override
            public void apply(StreamQuery query) {
                query.setLimit(10000);
                if (!includingDisabled) {
                    query.appendCondition(PairQueryCondition.LongQueryCondition.eq("searchable", UITemplate.DISABLED, (long) BooleanUtils.FALSE));
                }
            }
        });
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
                .timestamp(UITemplateBuilder.UI_TEMPLATE_TIMESTAMP)
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
