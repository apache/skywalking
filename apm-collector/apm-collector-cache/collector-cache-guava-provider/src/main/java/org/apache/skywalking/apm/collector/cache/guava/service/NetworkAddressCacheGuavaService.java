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

package org.apache.skywalking.apm.collector.cache.guava.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import org.apache.skywalking.apm.collector.cache.service.NetworkAddressCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.cache.INetworkAddressCacheDAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

import static java.util.Objects.isNull;
import static java.util.Objects.nonNull;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressCacheGuavaService implements NetworkAddressCacheService {

    private final Logger logger = LoggerFactory.getLogger(NetworkAddressCacheGuavaService.class);

    private final Cache<String, Integer> addressCache = CacheBuilder.newBuilder().initialCapacity(1000).maximumSize(5000).build();
    private final Cache<Integer, NetworkAddress> idCache = CacheBuilder.newBuilder().initialCapacity(1000).maximumSize(5000).build();

    private final ModuleManager moduleManager;
    private INetworkAddressCacheDAO networkAddressCacheDAO;

    public NetworkAddressCacheGuavaService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private INetworkAddressCacheDAO getNetworkAddressCacheDAO() {
        if (Objects.isNull(networkAddressCacheDAO)) {
            this.networkAddressCacheDAO = moduleManager.find(StorageModule.NAME).getService(INetworkAddressCacheDAO.class);
        }
        return this.networkAddressCacheDAO;
    }

    public int getAddressId(String networkAddress) {
        return Optional.ofNullable(retrieveFromCache(addressCache, networkAddress,
            () -> getNetworkAddressCacheDAO().getAddressId(networkAddress))).orElse(0);
    }

    public NetworkAddress getAddress(int addressId) {
        return retrieveFromCache(idCache, addressId,  () -> getNetworkAddressCacheDAO().getAddressById(addressId));
    }


    private <K, V> V retrieveFromCache(Cache<K, V> cache, K key, Supplier<V> supplier) {
        V value = null;
        try {
            value = cache.get(key, supplier::get);
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (isNull(value)) {
            value = supplier.get();
            if (nonNull(value)) {
                cache.put(key, value);
            }
        }

        return value;
    }


}
