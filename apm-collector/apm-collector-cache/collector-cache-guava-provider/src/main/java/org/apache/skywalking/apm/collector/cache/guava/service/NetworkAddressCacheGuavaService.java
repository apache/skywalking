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
import org.apache.skywalking.apm.collector.core.util.Const;
import org.apache.skywalking.apm.collector.core.util.ObjectUtils;
import org.apache.skywalking.apm.collector.core.util.StringUtils;
import org.apache.skywalking.apm.collector.storage.StorageModule;
import org.apache.skywalking.apm.collector.storage.dao.cache.INetworkAddressCacheDAO;
import org.apache.skywalking.apm.collector.storage.table.register.NetworkAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author peng-yongsheng
 */
public class NetworkAddressCacheGuavaService implements NetworkAddressCacheService {

    private final Logger logger = LoggerFactory.getLogger(NetworkAddressCacheGuavaService.class);

    private final Cache<String, Integer> addressCache = CacheBuilder.newBuilder().initialCapacity(100).maximumSize(5000).build();

    private final ModuleManager moduleManager;
    private INetworkAddressCacheDAO networkAddressCacheDAO;

    public NetworkAddressCacheGuavaService(ModuleManager moduleManager) {
        this.moduleManager = moduleManager;
    }

    private INetworkAddressCacheDAO getNetworkAddressCacheDAO() {
        if (ObjectUtils.isEmpty(networkAddressCacheDAO)) {
            this.networkAddressCacheDAO = moduleManager.find(StorageModule.NAME).getService(INetworkAddressCacheDAO.class);
        }
        return this.networkAddressCacheDAO;
    }

    public int getAddressId(String networkAddress) {
        int addressId = 0;
        try {
            addressId = addressCache.get(networkAddress, () -> getNetworkAddressCacheDAO().getAddressId(networkAddress));

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

    private final Cache<Integer, String> idCache = CacheBuilder.newBuilder().maximumSize(5000).build();

    public String getAddress(int addressId) {
        String networkAddress = Const.EMPTY_STRING;
        try {
            networkAddress = idCache.get(addressId, () -> getNetworkAddressCacheDAO().getAddressById(addressId));
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        if (StringUtils.isEmpty(networkAddress)) {
            networkAddress = getNetworkAddressCacheDAO().getAddressById(addressId);
            if (StringUtils.isNotEmpty(networkAddress)) {
                idCache.put(addressId, networkAddress);
            }
        }
        return networkAddress;
    }

    private final Cache<Integer, NetworkAddress> addressObjCache = CacheBuilder.newBuilder().maximumSize(5000).build();

    @Override public boolean compare(int addressId, int spanLayer, int serverType) {
        try {
            NetworkAddress address = addressObjCache.get(addressId, () -> getNetworkAddressCacheDAO().getAddress(addressId));
            if (ObjectUtils.isNotEmpty(address)) {
                if (spanLayer != address.getSpanLayer() || serverType != address.getServerType()) {
                    return false;
                }
            }
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }
        return true;
    }
}
