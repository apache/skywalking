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

import org.apache.skywalking.library.elasticsearch.requests.search.Query;
import org.apache.skywalking.library.elasticsearch.requests.search.Search;
import org.apache.skywalking.library.elasticsearch.requests.search.SearchBuilder;
import org.apache.skywalking.library.elasticsearch.response.search.SearchHit;
import org.apache.skywalking.library.elasticsearch.response.search.SearchResponse;
import org.apache.skywalking.oap.server.core.profiling.continuous.storage.ContinuousProfilingPolicy;
import org.apache.skywalking.oap.server.core.storage.profiling.continuous.IContinuousProfilingPolicyDAO;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.ElasticSearchConverter;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.EsDAO;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.base.IndexController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ContinuousProfilingPolicyEsDAO extends EsDAO implements IContinuousProfilingPolicyDAO {
    public ContinuousProfilingPolicyEsDAO(ElasticSearchClient client) {
        super(client);
    }

    @Override
    public void savePolicy(ContinuousProfilingPolicy policy) throws IOException {
        final ContinuousProfilingPolicy.Builder builder = new ContinuousProfilingPolicy.Builder();
        final ElasticSearchConverter.ToStorage toStorage = new ElasticSearchConverter.ToStorage(ContinuousProfilingPolicy.INDEX_NAME);
        builder.entity2Storage(policy, toStorage);

        final boolean exist = getClient().existDoc(ContinuousProfilingPolicy.INDEX_NAME, policy.id().build());
        if (exist) {
            getClient().forceUpdate(ContinuousProfilingPolicy.INDEX_NAME, policy.id().build(), toStorage.obtain());
        } else {
            getClient().forceInsert(ContinuousProfilingPolicy.INDEX_NAME, policy.id().build(), toStorage.obtain());
        }
    }

    @Override
    public List<ContinuousProfilingPolicy> queryPolicies(List<String> serviceIdList) throws IOException {
        final String index =
            IndexController.LogicIndicesRegister.getPhysicalTableName(ContinuousProfilingPolicy.INDEX_NAME);
        final SearchBuilder search = Search.builder()
            .query(Query.terms(ContinuousProfilingPolicy.SERVICE_ID, serviceIdList))
            .size(serviceIdList.size());

        return buildPolicies(getClient().search(index, search.build()));
    }

    private List<ContinuousProfilingPolicy> buildPolicies(SearchResponse response) {
        List<ContinuousProfilingPolicy> policies = new ArrayList<>();
        for (SearchHit hit : response.getHits()) {
            final Map<String, Object> sourceAsMap = hit.getSource();
            final ContinuousProfilingPolicy.Builder builder = new ContinuousProfilingPolicy.Builder();
            policies.add(builder.storage2Entity(new ElasticSearchConverter.ToEntity(ContinuousProfilingPolicy.INDEX_NAME, sourceAsMap)));
        }
        return policies;
    }

}