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
import java.util.function.Function;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.hierarchy.instance.InstanceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.hierarchy.service.ServiceHierarchyRelationTraffic;
import org.apache.skywalking.oap.server.core.storage.query.IHierarchyQueryDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchScroller;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

public class HierarchyQueryEsDAO extends EsDAO implements IHierarchyQueryDAO {
    private final int queryMaxSize;
    private final int scrollingBatchSize;

    protected final Function<SearchHit, ServiceHierarchyRelationTraffic> serviceRelationsFunction = hit -> {
        final var sourceAsMap = hit.getSource();
        final var builder = new ServiceHierarchyRelationTraffic.Builder();
        return builder.storage2Entity(
            new ElasticSearchConverter.ToEntity(ServiceHierarchyRelationTraffic.INDEX_NAME, sourceAsMap));
    };

    protected final Function<SearchHit, InstanceHierarchyRelationTraffic> instanceRelationsFunction = hit -> {
        final var sourceAsMap = hit.getSource();
        final var builder = new InstanceHierarchyRelationTraffic.Builder();
        return builder.storage2Entity(
            new ElasticSearchConverter.ToEntity(InstanceHierarchyRelationTraffic.INDEX_NAME, sourceAsMap));
    };

    public HierarchyQueryEsDAO(final ElasticSearchClient client,
                               final StorageModuleElasticsearchConfig config) {
        super(client);
        this.queryMaxSize = config.getMetadataQueryMaxSize();
        this.scrollingBatchSize = config.getScrollingBatchSize();
    }

    @Override
    public List<ServiceHierarchyRelationTraffic> readAllServiceHierarchyRelations() {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(ServiceHierarchyRelationTraffic.INDEX_NAME);

        final int batchSize = Math.min(queryMaxSize, scrollingBatchSize);
        final BoolQueryBuilder query = Query.bool();
        final SearchBuilder search = Search.builder().query(query).size(batchSize);
        if (IndexController.LogicIndicesRegister.isMergedTable(ServiceHierarchyRelationTraffic.INDEX_NAME)) {
            query.must(Query.term(
                IndexController.LogicIndicesRegister.METRIC_TABLE_NAME,
                ServiceHierarchyRelationTraffic.INDEX_NAME
            ));
        }

        final var scroller = ElasticSearchScroller
            .<ServiceHierarchyRelationTraffic>builder()
            .client(getClient())
            .search(search.build())
            .index(index)
            .queryMaxSize(queryMaxSize)
            .resultConverter(serviceRelationsFunction)
            .build();
        return scroller.scroll();
    }

    @Override
    public List<InstanceHierarchyRelationTraffic> readInstanceHierarchyRelations(final String instanceId, final String layer) {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(InstanceHierarchyRelationTraffic.INDEX_NAME);
        int layerValue = Layer.valueOf(layer).value();
        final BoolQueryBuilder instanceSide =
            Query.bool()
                 .must(Query.term(InstanceHierarchyRelationTraffic.INSTANCE_ID, instanceId))
                 .must(Query.term(InstanceHierarchyRelationTraffic.SERVICE_LAYER, layerValue));

        final BoolQueryBuilder relatedInstanceSide =
            Query.bool()
                 .must(Query.term(InstanceHierarchyRelationTraffic.RELATED_INSTANCE_ID, instanceId))
                 .must(Query.term(InstanceHierarchyRelationTraffic.RELATED_SERVICE_LAYER, layerValue));

        final BoolQueryBuilder instanceQuery =
            Query.bool()
                 .should(instanceSide)
                 .should(relatedInstanceSide);

        final BoolQueryBuilder query =
            Query.bool()
                 .must(instanceQuery);
        if (IndexController.LogicIndicesRegister.isMergedTable(InstanceHierarchyRelationTraffic.INDEX_NAME)) {
            query.must(Query.term(
                IndexController.LogicIndicesRegister.METRIC_TABLE_NAME,
                InstanceHierarchyRelationTraffic.INDEX_NAME
            ));
        }
        final SearchBuilder search = Search.builder().query(query);
        final SearchResponse response = getClient().search(index, search.build());
        return buildInstanceHierarchyRelations(response);
    }

    private List<InstanceHierarchyRelationTraffic> buildInstanceHierarchyRelations(SearchResponse response) {
        List<InstanceHierarchyRelationTraffic> relations = new ArrayList<>();
        for (SearchHit searchHit : response.getHits()) {
            relations.add(instanceRelationsFunction.apply(searchHit));
        }
        return relations;
    }
}
