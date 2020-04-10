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

package org.apache.skywalking.oap.server.core.cache;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.core.CoreModuleConfig;
import org.apache.skywalking.oap.server.core.analysis.manual.networkalias.NetworkAddressAlias;
import org.apache.skywalking.oap.server.library.module.Service;

/**
 * NetworkAddressAliasCache set the temporary network address - service/instance mapping in the memory cache. This data
 * was original analysis from reference of trace span.
 */
@Slf4j
public class NetworkAddressAliasCache implements Service {
    private final Cache<String, NetworkAddressAlias> networkAddressAliasCache;

    public NetworkAddressAliasCache(CoreModuleConfig moduleConfig) {
        long initialSize = moduleConfig.getMaxSizeOfNetworkAddressAlias() / 10L;
        int initialCapacitySize = (int) (initialSize > Integer.MAX_VALUE ? Integer.MAX_VALUE : initialSize);

        networkAddressAliasCache = CacheBuilder.newBuilder()
                                               .initialCapacity(initialCapacitySize)
                                               .maximumSize(moduleConfig.getMaxSizeOfNetworkAddressAlias())
                                               .build();
    }

    /**
     * @return NULL if alias doesn't exist or has been loaded in the cache.
     */
    public NetworkAddressAlias get(String address) {
        return networkAddressAliasCache.getIfPresent(address);
    }

    void load(List<NetworkAddressAlias> networkAddressAliasList) {
        networkAddressAliasList.forEach(networkAddressAlias -> {
            networkAddressAliasCache.put(networkAddressAlias.getAddress(), networkAddressAlias);
        });
    }

    long currentSize() {
        return networkAddressAliasCache.size();
    }
}
