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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ManagementCRUDEsDAO;

@Slf4j
public class UITemplateManagementEsDAO extends ManagementCRUDEsDAO implements UITemplateManagementDAO {
    public UITemplateManagementEsDAO(ElasticSearchClient client, StorageBuilder storageBuilder) {
        super(client, storageBuilder);
    }

    @Override
    public DashboardConfiguration getTemplate(final String id) throws IOException {
        UITemplate uiTemplate = (UITemplate) super.getById(UITemplate.INDEX_NAME, id);
        if (uiTemplate != null) {
            return new DashboardConfiguration().fromEntity(uiTemplate);
        }
        return null;
    }

    @Override
    public List<DashboardConfiguration> getAllTemplates(final Boolean includingDisabled) {
        final BoolQueryBuilder boolQuery = Query.bool();
        boolQuery.must(Query.term(IndexController.LogicIndicesRegister.MANAGEMENT_TABLE_NAME, UITemplate.INDEX_NAME));
        if (!includingDisabled) {
            boolQuery.must(Query.term(
                UITemplate.DISABLED,
                BooleanUtils.booleanToValue(includingDisabled)
            ));
        }

        final SearchBuilder search =
            Search.builder().query(boolQuery)
                  // It is impossible we have 10000+ templates.
                  .size(10000);

        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(UITemplate.INDEX_NAME);
        final SearchResponse response = getClient().search(index, search.build());

        final List<DashboardConfiguration> configs = new ArrayList<>();
        final UITemplate.Builder builder = new UITemplate.Builder();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSource();

            final UITemplate uiTemplate = builder.storage2Entity(
                new ElasticSearchConverter.ToEntity(UITemplate.INDEX_NAME, sourceAsMap));
            configs.add(new DashboardConfiguration().fromEntity(uiTemplate));
        }
        return configs;
    }

    @Override
    public TemplateChangeStatus addTemplate(final DashboardSetting setting) throws IOException {
        final UITemplate uiTemplate = setting.toEntity();
        if (super.create(UITemplate.INDEX_NAME, uiTemplate)) {
            return TemplateChangeStatus.builder().status(true).id(uiTemplate.getTemplateId()).build();
        }
        return TemplateChangeStatus.builder().status(false).id(uiTemplate.getTemplateId())
                                   .message("Template already exists").build();
    }

    @Override
    public TemplateChangeStatus changeTemplate(final DashboardSetting setting) throws IOException {
        final UITemplate uiTemplate = setting.toEntity();
        if (super.update(UITemplate.INDEX_NAME, uiTemplate)) {
            return TemplateChangeStatus.builder().status(true).id(uiTemplate.getTemplateId()).build();
        }
        return TemplateChangeStatus.builder().status(false).id(uiTemplate.getTemplateId())
                                   .message("Can't find the template").build();
    }

    @Override
    public TemplateChangeStatus disableTemplate(final String id) throws IOException {
        UITemplate uiTemplate = (UITemplate) super.getById(UITemplate.INDEX_NAME, id);
        if (uiTemplate != null) {
            uiTemplate.setDisabled(BooleanUtils.TRUE);
            super.update(UITemplate.INDEX_NAME, uiTemplate);
            return TemplateChangeStatus.builder().status(true).id(id).build();
        }
        return TemplateChangeStatus.builder().status(false).id(id).message("Can't find the template")
                                   .build();
    }
}
