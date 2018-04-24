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
import org.apache.skywalking.apm.collector.cache.guava.CacheUtils;
import org.apache.skywalking.apm.collector.cache.service.NetworkAddressCacheService;
import org.apache.skywalking.apm.collector.core.module.ModuleManager;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.cache.INetworkAddressCacheDAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddress;

import static java.util.Objects.isNull;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressCacheGuavaService implements NetworkAddressCacheService {

    private final Cache<String, Integer> addressCache = CacheBuilder.newBuilder().initialCapacity(1000).maximumSize(5000).build();
    private final Cache<Integer, NetworkAddress> idCache = CacheBuilder.newBuilder().initialCapacity(1000).maximumSize(5000).build();

    private final ModuleManager moduleManager;
    private INetworkAddressCacheDAO networkAddressCacheDAO;

    public NetworkAddressCacheGuavaService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private INetworkAddressCacheDAO getNetworkAddressCacheDAO() {
        if (isNull(networkAddressCacheDAO)) {
            this.networkAddressCacheDAO = moduleManager.find(StorageModule.NAME).getService(INetworkAddressCacheDAO.class);
        }
        return this.networkAddressCacheDAO;
    }

    public int getAddressId(String networkAddress) {
        return CacheUtils.retrieveOrElse(addressCache, networkAddress,
            () -> getNetworkAddressCacheDAO().getAddressId(networkAddress), 0);
    }


    public NetworkAddress getAddress(int addressId) {
        return CacheUtils.retrieve(idCache, addressId, () -> getNetworkAddressCacheDAO().getAddressById(addressId));
    }
}
