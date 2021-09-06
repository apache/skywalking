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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.response.Document;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

@Slf4j
public class UITemplateManagementEsDAO extends EsDAO implements UITemplateManagementDAO {
    public UITemplateManagementEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<DashboardConfiguration> getAllTemplates(final Boolean includingDisabled) {
        final BoolQueryBuilder boolQuery = Query.bool();
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

            final UITemplate uiTemplate = builder.storage2Entity(sourceAsMap);
            configs.add(new DashboardConfiguration().fromEntity(uiTemplate));
        }
        return configs;
    }

    @Override
    public TemplateChangeStatus addTemplate(final DashboardSetting setting) {
        try {
            final UITemplate.Builder builder = new UITemplate.Builder();
            final UITemplate uiTemplate = setting.toEntity();

            final boolean exist = getClient().existDoc(UITemplate.INDEX_NAME, uiTemplate.id());
            if (exist) {
                return TemplateChangeStatus.builder().status(false).message("Template exists")
                                           .build();
            }

            final Map<String, Object> xContentBuilder = builder.entity2Storage(uiTemplate);
            getClient().forceInsert(UITemplate.INDEX_NAME, uiTemplate.id(), xContentBuilder);
            return TemplateChangeStatus.builder().status(true).build();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return TemplateChangeStatus.builder().status(false).message("Can't add a new template")
                                       .build();
        }
    }

    @Override
    public TemplateChangeStatus changeTemplate(final DashboardSetting setting) {
        try {
            final UITemplate.Builder builder = new UITemplate.Builder();
            final UITemplate uiTemplate = setting.toEntity();

            final boolean exist = getClient().existDoc(UITemplate.INDEX_NAME, uiTemplate.id());
            if (!exist) {
                return TemplateChangeStatus.builder().status(false)
                                           .message("Can't find the template").build();
            }

            final Map<String, Object> xContentBuilder = builder.entity2Storage(uiTemplate);
            getClient().forceUpdate(UITemplate.INDEX_NAME, uiTemplate.id(), xContentBuilder);
            return TemplateChangeStatus.builder().status(true).build();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return TemplateChangeStatus.builder().status(false).message("Can't find the template")
                                       .build();
        }
    }

    @Override
    public TemplateChangeStatus disableTemplate(final String name) {
        final Optional<Document> response = getClient().get(UITemplate.INDEX_NAME, name);
        if (response.isPresent()) {
            final UITemplate.Builder builder = new UITemplate.Builder();
            final UITemplate uiTemplate = builder.storage2Entity(response.get().getSource());
            uiTemplate.setDisabled(BooleanUtils.TRUE);

            final Map<String, Object> xContentBuilder = builder.entity2Storage(uiTemplate);
            getClient().forceUpdate(UITemplate.INDEX_NAME, uiTemplate.id(), xContentBuilder);
            return TemplateChangeStatus.builder().status(true).build();
        } else {
            return TemplateChangeStatus.builder().status(false).message("Can't find the template")
                                       .build();
        }
    }
}
