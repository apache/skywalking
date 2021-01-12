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

package org.apache.skywalking.oap.server.configuration.etcd;

import java.net.URI;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import mousio.client.promises.ResponsePromise;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.promises.EtcdResponsePromise;
import mousio.etcd4j.responses.EtcdErrorCode;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.ConfigWatcherRegister;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EtcdConfigWatcherRegister extends ConfigWatcherRegister {

    private static final Logger LOGGER = LoggerFactory.getLogger(EtcdConfigWatcherRegister.class);

    /**
     * server settings for Etcd configuration
     */
    private EtcdServerSettings settings;

    /**
     * etcd client.
     */
    private final EtcdClient client;

    private final Map<String, ResponsePromise.IsSimplePromiseResponseHandler> listenersByKey;

    private final Map<String, Optional<String>> configItemKeyedByName;

    private final Map<String, EtcdResponsePromise<EtcdKeysResponse>> responsePromiseByKey;

    public EtcdConfigWatcherRegister(EtcdServerSettings settings) {
        super(settings.getPeriod());
        this.settings = settings;
        this.configItemKeyedByName = new ConcurrentHashMap<>();
        this.client = new EtcdClient(EtcdUtils.parse(settings).toArray(new URI[] {}));
        this.listenersByKey = new ConcurrentHashMap<>();
        responsePromiseByKey = new ConcurrentHashMap<>();
    }

    @Override
    public Optional<ConfigTable> readConfig(Set<String> keys) {
        removeUninterestedKeys(keys);
        registerKeyListeners(keys);
        final ConfigTable table = new ConfigTable();

        for (Map.Entry<String, Optional<String>> entry : configItemKeyedByName.entrySet()) {
            final String key = entry.getKey();
            final Optional<String> value = entry.getValue();

            if (value.isPresent()) {
                table.add(new ConfigTable.ConfigItem(key, value.get()));
            } else {
                table.add(new ConfigTable.ConfigItem(key, null));
            }
        }

        return Optional.of(table);
    }

    private void registerKeyListeners(final Set<String> keys) {
        for (final String key : keys) {
            String dataId = "/" + settings.getGroup() + "/" + key;
            if (listenersByKey.containsKey(dataId)) {
                continue;
            }

            listenersByKey.putIfAbsent(dataId, p -> {
                onDataValueChanged(p, dataId);
            });

            try {
                EtcdResponsePromise<EtcdKeysResponse> responsePromise = client.get(dataId).waitForChange().send();
                responsePromise.addListener(listenersByKey.get(dataId));
                responsePromiseByKey.putIfAbsent(dataId, responsePromise);

                // the key is newly added, read the config for the first time
                EtcdResponsePromise<EtcdKeysResponse> promise = client.get(dataId).send();
                onDataValueChanged(promise, dataId);
            } catch (Exception e) {
                throw new EtcdConfigException("wait for etcd value change fail", e);
            }
        }
    }

    private void removeUninterestedKeys(final Set<String> interestedKeys) {
        final Set<String> uninterestedKeys = new HashSet<>(listenersByKey.keySet());
        uninterestedKeys.removeAll(interestedKeys);

        uninterestedKeys.forEach(k -> {
            final ResponsePromise.IsSimplePromiseResponseHandler listener = listenersByKey.remove(k);
            if (listener != null) {
                responsePromiseByKey.remove(k).removeListener(listener);
            }
        });
    }

    private void onDataValueChanged(ResponsePromise<EtcdKeysResponse> promise, String dataId) {
        String key = getRealKey(dataId, settings.getGroup());
        try {
            EtcdKeysResponse.EtcdNode node = promise.get().getNode();
            String value = node.getValue();
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Etcd config changed: {}: {}", key, node.getValue());
            }

            configItemKeyedByName.put(key, Optional.ofNullable(value));
        } catch (Exception e) {
            if (e instanceof EtcdException) {
                if (EtcdErrorCode.KeyNotFound == ((EtcdException) e).errorCode) {
                    configItemKeyedByName.put(key, Optional.empty());
                    return;
                }
            }
            throw new EtcdConfigException("wait for value changed fail", e);
        }
    }

    /**
     * get real key in etcd cluster which is removed "/${group}" from the key retrive from etcd.
     */
    private String getRealKey(String key, String group) {
        int index = key.indexOf(group);
        if (index <= 0) {
            throw new RuntimeException("the group doesn't match");
        }
        String realKey = key.substring(index + group.length() + 1);
        return realKey;
    }
}
