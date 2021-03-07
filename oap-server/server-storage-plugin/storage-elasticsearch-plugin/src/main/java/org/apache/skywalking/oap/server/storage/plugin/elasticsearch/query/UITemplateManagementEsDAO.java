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
import org.apache.skywalking.oap.server.core.management.ui.template.UITemplate;
import org.apache.skywalking.oap.server.core.query.input.DashboardSetting;
import org.apache.skywalking.oap.server.core.query.type.DashboardConfiguration;
import org.apache.skywalking.oap.server.core.query.type.TemplateChangeStatus;
import org.apache.skywalking.oap.server.core.storage.management.UITemplateManagementDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.util.BooleanUtils;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;

@Slf4j
public class UITemplateManagementEsDAO extends EsDAO implements UITemplateManagementDAO {
    public UITemplateManagementEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public List<DashboardConfiguration> getAllTemplates(final Boolean includingDisabled) throws IOException {
        SearchSourceBuilder sourceBuilder = SearchSourceBuilder.searchSource();

        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        if (!includingDisabled) {
            boolQueryBuilder.must()
                            .add(QueryBuilders.termQuery(
                                UITemplate.DISABLED,
                                BooleanUtils.booleanToValue(includingDisabled)
                            ));
        }

        sourceBuilder.query(boolQueryBuilder);
        //It is impossible we have 10000+ templates.
        sourceBuilder.size(10000);

        SearchResponse response = getClient().search(
            IndexController.LogicIndicesRegister.getPhysicalTableName(UITemplate.INDEX_NAME), sourceBuilder);

        List<DashboardConfiguration> configs = new ArrayList<>();
        final UITemplate.Builder builder = new UITemplate.Builder();
        for (SearchHit searchHit : response.getHits()) {
            Map<String, Object> sourceAsMap = searchHit.getSourceAsMap();

            final UITemplate uiTemplate = builder.storage2Entity(sourceAsMap);
            configs.add(new DashboardConfiguration().fromEntity(uiTemplate));
        }
        return configs;
    }

    @Override
    public TemplateChangeStatus addTemplate(final DashboardSetting setting) throws IOException {
        try {
            final UITemplate.Builder builder = new UITemplate.Builder();
            final UITemplate uiTemplate = setting.toEntity();

            final GetResponse response = getClient().get(UITemplate.INDEX_NAME, uiTemplate.id());
            if (response.isExists()) {
                return TemplateChangeStatus.builder().status(false).message("Template exists").build();
            }

            XContentBuilder xContentBuilder = map2builder(builder.entity2Storage(uiTemplate));
            getClient().forceInsert(UITemplate.INDEX_NAME, uiTemplate.id(), xContentBuilder);
            return TemplateChangeStatus.builder().status(true).build();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return TemplateChangeStatus.builder().status(false).message("Can't add a new template").build();
        }
    }

    @Override
    public TemplateChangeStatus changeTemplate(final DashboardSetting setting) throws IOException {
        try {
            final UITemplate.Builder builder = new UITemplate.Builder();
            final UITemplate uiTemplate = setting.toEntity();

            final GetResponse response = getClient().get(UITemplate.INDEX_NAME, uiTemplate.id());
            if (!response.isExists()) {
                return TemplateChangeStatus.builder().status(false).message("Can't find the template").build();
            }

            XContentBuilder xContentBuilder = map2builder(builder.entity2Storage(uiTemplate));
            getClient().forceUpdate(UITemplate.INDEX_NAME, uiTemplate.id(), xContentBuilder);
            return TemplateChangeStatus.builder().status(true).build();
        } catch (IOException e) {
            log.error(e.getMessage(), e);
            return TemplateChangeStatus.builder().status(false).message("Can't find the template").build();
        }
    }

    @Override
    public TemplateChangeStatus disableTemplate(final String name) throws IOException {
        final GetResponse response = getClient().get(UITemplate.INDEX_NAME, name);
        if (response.isExists()) {
            final UITemplate.Builder builder = new UITemplate.Builder();
            final UITemplate uiTemplate = builder.storage2Entity(response.getSourceAsMap());
            uiTemplate.setDisabled(BooleanUtils.TRUE);

            XContentBuilder xContentBuilder = map2builder(builder.entity2Storage(uiTemplate));
            getClient().forceUpdate(UITemplate.INDEX_NAME, uiTemplate.id(), xContentBuilder);
            return TemplateChangeStatus.builder().status(true).build();
        } else {
            return TemplateChangeStatus.builder().status(false).message("Can't find the template").build();
        }
    }
}
