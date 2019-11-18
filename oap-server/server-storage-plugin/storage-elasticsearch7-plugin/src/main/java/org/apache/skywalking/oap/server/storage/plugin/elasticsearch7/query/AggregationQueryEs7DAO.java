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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch7.query;

import org.apache.skywalking.oap.server.core.analysis.metrics.Metrics;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;
import org.apache.skywalking.oap.server.storage.plugin.elasticsearch.query.AggregationQueryEsDAO;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.BucketOrder;
import org.elasticsearch.search.aggregations.bucket.terms.TermsAggregationBuilder;

/**
 * @author peng-yongsheng
 * @author kezhenxu94
 */
public class AggregationQueryEs7DAO extends AggregationQueryEsDAO {

    public AggregationQueryEs7DAO(ElasticSearchClient client) {
        super(client);
    }

    protected TermsAggregationBuilder aggregationBuilder(final String valueCName, final int topN, final boolean asc) {
        return AggregationBuilders
            .terms(Metrics.ENTITY_ID)
            .field(Metrics.ENTITY_ID)
            .order(BucketOrder.aggregation(valueCName, asc))
            .size(topN)
            .subAggregation(
                AggregationBuilders.avg(valueCName).field(valueCName)
            );
    }
}
