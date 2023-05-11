/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query;

import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.analysis.manual.process.ServiceLabelRecord;
import org.apache.skywalking.oap.server.core.storage.profiling.ebpf.IServiceLabelDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.StorageModuleElasticsearchConfig;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

import java.util.List;
import java.util.stream.Collectors;

public class ServiceLabelEsDAO extends EsDAO implements IServiceLabelDAO {
    private int maxSize;

    public ServiceLabelEsDAO(ElasticSearchClient client, StorageModuleElasticsearchConfig config) {
        super(client);
        this.maxSize = config.getMetadataQueryMaxSize();
    }

    @Override
    public List<String> queryAllLabels(String serviceId) {
        final String index =
                IndexController.LogicIndicesRegister.getPhysicalTableName(ServiceLabelRecord.INDEX_NAME);
        final BoolQueryBuilder query = Query.bool();
        if (IndexController.LogicIndicesRegister.isMergedTable(ServiceLabelRecord.INDEX_NAME)) {
            query.must(Query.term(IndexController.LogicIndicesRegister.METRIC_TABLE_NAME, ServiceLabelRecord.INDEX_NAME));
        }
        query.must(Query.term(ServiceLabelRecord.SERVICE_ID, serviceId));
        final SearchBuilder search = Search.builder().query(query).size(maxSize);

        final SearchResponse response = getClient().search(index, search.build());
        return response.getHits().getHits().stream().map(this::parseLabel).collect(Collectors.toList());
    }

    private String parseLabel(final SearchHit hit) {
        return (String) hit.getSource().get(ServiceLabelRecord.LABEL);
    }
}
