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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.library.elasticsearch.requests.search.BoolQueryBuilder;
import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.management.runtimerule.RuntimeRule;
import org.apache.skywalking.oap.server.core.storage.management.RuntimeRuleManagementDAO;
import org.apache.skywalking.oap.server.core.storage.type.StorageBuilder;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ManagementCRUDEsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

@Slf4j
public class RuntimeRuleManagementEsDAO extends ManagementCRUDEsDAO implements RuntimeRuleManagementDAO {
    @SuppressWarnings({"rawtypes", "unchecked"})
    public RuntimeRuleManagementEsDAO(final ElasticSearchClient client,
                                      final StorageBuilder storageBuilder) {
        super(client, storageBuilder);
    }

    @Override
    public List<RuntimeRuleFile> getAll() throws IOException {
        final BoolQueryBuilder boolQuery = Query.bool();
        boolQuery.must(Query.term(
            IndexController.LogicIndicesRegister.MANAGEMENT_TABLE_NAME, RuntimeRule.INDEX_NAME));
        // No upper bound on rule count — 10000 is the safety ceiling and matches the UITemplate
        // DAO convention. Operators that approach this limit should split rules across files.
        final SearchBuilder search = Search.builder().query(boolQuery).size(10000);
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(RuntimeRule.INDEX_NAME);
        final SearchResponse response = getClient().search(index, search.build());

        final List<RuntimeRuleFile> files = new ArrayList<>();
        for (final SearchHit hit : response.getHits()) {
            final Map<String, Object> src = hit.getSource();
            files.add(new RuntimeRuleFile(
                asString(src.get(RuntimeRule.CATALOG)),
                asString(src.get(RuntimeRule.NAME)),
                asString(src.get(RuntimeRule.CONTENT)),
                asString(src.get(RuntimeRule.STATUS)),
                asLong(src.get(RuntimeRule.UPDATE_TIME))
            ));
        }
        return files;
    }

    @Override
    public void save(final RuntimeRule rule) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(RuntimeRule.INDEX_NAME);
        final String docId = RuntimeRule.INDEX_NAME + "_" + rule.getCatalog() + "_" + rule.getName();
        final Map<String, Object> source = new HashMap<>();
        source.put(RuntimeRule.CATALOG, rule.getCatalog());
        source.put(RuntimeRule.NAME, rule.getName());
        source.put(RuntimeRule.CONTENT, rule.getContent());
        source.put(RuntimeRule.STATUS, rule.getStatus());
        source.put(RuntimeRule.UPDATE_TIME, rule.getUpdateTime());
        source.put(IndexController.LogicIndicesRegister.MANAGEMENT_TABLE_NAME, RuntimeRule.INDEX_NAME);
        // forceInsert maps to the ES `index` API which is upsert-by-_id; existing docs with
        // the same id are replaced. The base class `create` / `update` helpers explicitly
        // gate on existDoc, which is exactly the bug we're avoiding here.
        getClient().forceInsert(index, docId, source);
    }

    @Override
    public void delete(final String catalog, final String name) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(RuntimeRule.INDEX_NAME);
        // Elasticsearch document id format for management-data records follows the StorageID
        // composite — see RuntimeRule.id() which appends (catalog, name). IndexController
        // passes the id through to the _id field on insert, so delete by that same id.
        final String docId = RuntimeRule.INDEX_NAME + "_" + catalog + "_" + name;
        getClient().deleteById(index, docId);
    }

    private static String asString(final Object v) {
        return v == null ? null : v.toString();
    }

    private static long asLong(final Object v) {
        if (v == null) {
            return 0L;
        }
        if (v instanceof Number) {
            return ((Number) v).longValue();
        }
        return Long.parseLong(v.toString());
    }
}
