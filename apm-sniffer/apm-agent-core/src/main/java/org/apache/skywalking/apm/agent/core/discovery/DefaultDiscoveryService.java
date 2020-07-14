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

package org.apache.skywalking.apm.agent.core.discovery;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.apache.skywalking.apm.agent.core.boot.Address;
import org.apache.skywalking.apm.agent.core.boot.DefaultImplementor;
import org.apache.skywalking.apm.agent.core.boot.DiscoveryService;
import org.apache.skywalking.apm.agent.core.conf.Config;
import org.apache.skywalking.apm.agent.core.plugin.PluginException;

@DefaultImplementor
public class DefaultDiscoveryService implements DiscoveryService {

    private List<Address> addresses;

    @Override
    public void prepare() throws Throwable {
        String backendService = Config.Collector.ServiceDiscorvery.Static.BACKEND_SERVICE;

        if (backendService.trim().length() == 0) {
            throw new PluginException("static backendService config is required in static service discovery plugin");
        }
        addresses = Arrays.stream(backendService.split(",")).map(item -> {
            String[] remotes = item.split(":");
            return new Address(remotes[0], Integer.parseInt(remotes[1]));
        }).collect(Collectors.toList());

    }

    @Override
    public void boot() throws Throwable {

    }

    @Override
    public void onComplete() throws Throwable {

    }

    @Override
    public void shutdown() throws Throwable {

    }

    @Override
    public List<Address> queryRemoteAddresses() {
        return Objects.nonNull(addresses) ? addresses : new ArrayList<>();
    }

}
