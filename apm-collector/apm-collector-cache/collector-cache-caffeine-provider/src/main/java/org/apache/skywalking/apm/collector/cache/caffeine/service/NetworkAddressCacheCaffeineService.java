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

package org.apache.skywalking.apm.collector.cache.caffeine.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.apache.skywalking.apm.collector.cache.service.NetworkAddressCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.cache.INetworkAddressCacheDAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressCacheCaffeineService implements NetworkAddressCacheService {

    private final Logger logger = LoggerFactory.getLogger(NetworkAddressCacheCaffeineService.class);

    private final Cache<String, Integer> addressCache = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).initialCapacity(1000).maximumSize(5000).build();

    private final ModuleManager moduleManager;
    private INetworkAddressCacheDAO networkAddressCacheDAO;

    public NetworkAddressCacheCaffeineService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private INetworkAddressCacheDAO getNetworkAddressCacheDAO() {
        if (isNull(networkAddressCacheDAO)) {
            this.networkAddressCacheDAO = moduleManager.find(StorageModule.NAME).getService(INetworkAddressCacheDAO.class);
        }
        return this.networkAddressCacheDAO;
    }

    public int getAddressId(String networkAddress) {
        int addressId = 0;
        try {
            Integer value = addressCache.get(networkAddress, key -> getNetworkAddressCacheDAO().getAddressId(key));
            addressId = value == null ? 0 : value;

            if (addressId == 0) {
                addressId = getNetworkAddressCacheDAO().getAddressId(networkAddress);
                if (addressId != 0) {
                    addressCache.put(networkAddress, addressId);
                }
            }
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        return addressId;
    }

    private final Cache<Integer, NetworkAddress> idCache = Caffeine.newBuilder().expireAfterWrite(10, TimeUnit.SECONDS).initialCapacity(1000).maximumSize(5000).build();

    public NetworkAddress getAddress(int addressId) {
        NetworkAddress networkAddress = null;
        try {
            networkAddress = idCache.get(addressId, key -> getNetworkAddressCacheDAO().getAddressById(key));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (isNull(networkAddress)) {
            networkAddress = getNetworkAddressCacheDAO().getAddressById(addressId);
            if (StringUtils.isNotEmpty(networkAddress)) {
                idCache.put(addressId, networkAddress);
            }
        }
        return networkAddress;
    }
}
