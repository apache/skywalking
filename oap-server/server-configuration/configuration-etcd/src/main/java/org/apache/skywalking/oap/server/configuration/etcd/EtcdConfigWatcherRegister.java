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

import java.io.IOException;
import java.net.URI;
import java.util.Set;
import mousio.etcd4j.EtcdClient;
import mousio.etcd4j.promises.EtcdResponsePromise;
import mousio.etcd4j.responses.EtcdErrorCode;
import mousio.etcd4j.responses.EtcdException;
import mousio.etcd4j.responses.EtcdKeysResponse;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.ConfigWatcherRegister;
import org.apache.skywalking.oap.server.library.module.ModuleStartException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Alan Lau
 */
public class EtcdConfigWatcherRegister extends ConfigWatcherRegister {

    private final static Logger logger = LoggerFactory.getLogger(EtcdConfigWatcherRegister.class);

    private EtcdServerSettings settings;

    private EtcdClient client;

    @Override public ConfigTable readConfig(Set<String> keys) {

        if (client == null) {
            try {
                client = new EtcdClient(EtcdUtils.parse(settings).toArray(new URI[] {}));
            } catch (ModuleStartException e) {
                logger.error(e.getMessage(), e);
            }

            EtcdResponsePromise<EtcdKeysResponse> promise;
            try {
                promise = client.get(settings.getClusterName()).waitForChange().send();
                promise.addListener(responsePromise -> {
                    onDataValueChanged();
                });

            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }

        final ConfigTable table = new ConfigTable();
        keys.forEach(registryKey -> {
            String key = new StringBuilder("/").append(settings.getGroup()).append("/").append(registryKey).toString();
            try {
                EtcdResponsePromise<EtcdKeysResponse> promise = client.get(key).send();
                EtcdKeysResponse response = promise.get();
                table.add(new ConfigTable.ConfigItem(getRealKey(key, settings.getGroup()), response.getNode().getValue()));
            } catch (EtcdException e) {
                if (e.getErrorCode() == EtcdErrorCode.KeyNotFound) {
                    table.add(new ConfigTable.ConfigItem(getRealKey(key, settings.getGroup()), null));
                } else {
                    logger.error(e.getMessage(), e);
                }
            } catch (Exception e1) {
                logger.error(e1.getMessage(), e1);
            }
        });

        return table;
    }

    public EtcdConfigWatcherRegister(EtcdServerSettings settings) {
        this.settings = settings;
    }

    private void onDataValueChanged() {

    }

    private String getRealKey(String key, String group) {
        int index = key.indexOf(group);
        if (index <= 0) {
            throw new RuntimeException("the group doesn't match");
        }
        String realKey = key.substring(index + group.length() + 1);
        return realKey;
    }
}
