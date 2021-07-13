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
 */

package org.apache.skywalking.oap.server.configuration.etcd;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.ClientBuilder;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.kv.GetResponse;
import java.nio.charset.Charset;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.apm.util.StringUtil;
import org.apache.skywalking.oap.server.configuration.api.ConfigTable;
import org.apache.skywalking.oap.server.configuration.api.ConfigWatcherRegister;

@Slf4j
public class EtcdConfigWatcherRegister extends ConfigWatcherRegister {

    private final KV client;

    public EtcdConfigWatcherRegister(EtcdServerSettings setting) {
        super(setting.getPeriod());
        ClientBuilder builder = Client.builder()
                                      .authority(setting.getAuthority())
                                      .endpoints(setting.getEndpointArray());

        if (StringUtil.isNotEmpty(setting.getNamespace())) {
            builder.namespace(ByteSequence.from(setting.getNamespace(), Charset.defaultCharset()));
        }
        if (setting.isAuthentication()) {
            builder.user(ByteSequence.from(setting.getUser(), Charset.defaultCharset()))
                   .password(ByteSequence.from(setting.getPassword(), Charset.defaultCharset()));
        }
        client = builder.build().getKVClient();
    }

    @Override
    public Optional<ConfigTable> readConfig(final Set<String> keys) {
        ConfigTable table = new ConfigTable();
        keys.forEach(e -> {
            try {
                GetResponse response = client.get(ByteSequence.from(e, Charset.defaultCharset())).get();

                if (0 == response.getCount()) {
                    table.add(new ConfigTable.ConfigItem(e, null));
                } else {
                    response.getKvs().forEach(kv -> table.add(new ConfigTable.ConfigItem(
                                                                  kv.getKey().toString(Charset.defaultCharset()),
                                                                  kv.getValue().toString(Charset.defaultCharset())
                                                              )
                    ));
                }
            } catch (Exception exp) {
                throw new EtcdConfigException("Failed to read configuration", exp);
            }
        });
        return Optional.of(table);
    }

}
