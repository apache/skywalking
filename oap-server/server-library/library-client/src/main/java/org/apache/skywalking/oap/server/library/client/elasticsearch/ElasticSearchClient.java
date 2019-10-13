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

package org.apache.skywalking.oap.server.library.client.elasticsearch;

import java.io.IOException;
import java.util.List;

import com.google.gson.JsonObject;
import org.apache.skywalking.oap.server.library.client.Client;
import org.elasticsearch.action.bulk.BulkProcessor;
import org.elasticsearch.action.bulk.BulkRequest;
import org.elasticsearch.action.get.GetResponse;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.common.xcontent.XContentBuilder;
import org.elasticsearch.search.builder.SearchSourceBuilder;

/**
 * This class defines some methods that are needed for a storage plugin
 * to communicate with ElasticSearch
 *
 * @author peng-yongsheng
 * @author kezhenxu94
 */
public interface ElasticSearchClient extends Client {

    boolean createIndex(String indexName) throws IOException;

    boolean createIndex(String indexName, JsonObject settings, JsonObject mapping) throws IOException;

    List<String> retrievalIndexByAliases(String aliases) throws IOException;

    /**
     * If your indexName is retrieved from elasticsearch through {@link #retrievalIndexByAliases(String)} or some other
     * method and it already contains namespace. Then you should delete the index by this method, this method will no
     * longer concatenate namespace.
     *
     * https://github.com/apache/skywalking/pull/3017
     */
    boolean deleteByIndexName(String indexName) throws IOException;

    /**
     * If your indexName is obtained from metadata or configuration and without namespace. Then you should delete the
     * index by this method, this method automatically concatenates namespace.
     *
     * https://github.com/apache/skywalking/pull/3017
     */
    boolean deleteByModelName(String modelName) throws IOException;

    boolean isExistsIndex(String indexName) throws IOException;

    boolean isExistsTemplate(String indexName) throws IOException;

    boolean createTemplate(String indexName, JsonObject settings, JsonObject mapping) throws IOException;

    boolean deleteTemplate(String indexName) throws IOException;

    SearchResponse search(String indexName, SearchSourceBuilder searchSourceBuilder) throws IOException;

    GetResponse get(String indexName, String id) throws IOException;

    SearchResponse ids(String indexName, String[] ids) throws IOException;

    void forceInsert(String indexName, String id, XContentBuilder source) throws IOException;

    void forceUpdate(String indexName, String id, XContentBuilder source, long version) throws IOException;

    void forceUpdate(String indexName, String id, JsonObject source, long seqNumber, long primaryTerm) throws IOException;

    void forceUpdate(String indexName, String id, XContentBuilder source) throws IOException;

    ElasticSearchInsertRequest prepareInsert(String indexName, String id, XContentBuilder source);

    ElasticSearchUpdateRequest prepareUpdate(String indexName, String id, XContentBuilder source);

    int delete(String indexName, String timeBucketColumnName, long endTimeBucket) throws IOException;

    void synchronousBulk(BulkRequest request);

    BulkProcessor createBulkProcessor(int bulkActions, int flushInterval, int concurrentRequests);

    String formatIndexName(String indexName);
}
