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

package org.apache.skywalking.oap.server.storage.plugin.elasticsearch.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.apache.skywalking.oap.server.library.client.elasticsearch.ElasticSearchClient;

public class AliasIndexNameCache {

    private static final int ONE_DAY_SECONDS = 24 * 60 * 60;
    private ElasticSearchClient client;
    private Cache<String, List<String>> aliasCache;

    public AliasIndexNameCache(ElasticSearchClient client) {
        this.client = client;

        aliasCache = CacheBuilder.newBuilder()
            .initialCapacity(10000)
            .maximumSize(100000)
            .expireAfterWrite(ONE_DAY_SECONDS, TimeUnit.SECONDS)
            .build();
    }

    public boolean checkIndexExist(String indexName, String indName) throws IOException {
        List<String> indexList = aliasCache.getIfPresent(indName);
        if (Objects.isNull(indexList) || indexList.size() == 0) {
            List<String> aliases = client.retrievalIndexByAliases(indName);
            if (aliases.size() > 0) {
                aliasCache.put(indName, aliases);
                return aliases.contains(indexName);
            }
            return false;
        }
        return indexList.contains(indexName);
    }
}
