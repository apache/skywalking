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

package org.apache.skywalking.apm.webapp.proxy;

import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.netflix.client.config.CommonClientConfigKey;
import com.netflix.client.config.IClientConfig;
import com.netflix.loadbalancer.AbstractServerList;
import com.netflix.loadbalancer.Server;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class CollectorServerList extends AbstractServerList<Server> {
    private static final Logger logger = LoggerFactory.getLogger(CollectorServerList.class);

    private final CopyOnWriteArrayList<Server> servers = new CopyOnWriteArrayList<>();

    private final Gson gson = new Gson();

    private IClientConfig clientConfig;

    public List<Server> getInitialListOfServers() {
        return fetchServer();
    }

    public List<Server> getUpdatedListOfServers() {
        return fetchServer();
    }

    public void initWithNiwsConfig(IClientConfig clientConfig) {
        this.clientConfig = clientConfig;
    }

    protected List<Server> derive(String value) {
        List<Server> list = Lists.newArrayList();
        if (Strings.isNullOrEmpty(value)) {
            return list;
        }
        String[] serverArray = value.split(",");
        for (String s : serverArray) {
            list.add(new Server(s.trim()));
        }
        return list;
    }

    private List<Server> fetchServer() {
        for (Server server : derive(this.clientConfig.get(CommonClientConfigKey.ListOfServers))) {
            try {
                String uiServerResponse = HttpClientTools.INSTANCE.get("http://" + server.getHostPort() + "/ui/jetty", null);
                logger.debug("uiServerResponse: {}", uiServerResponse);
                JsonArray serverArray = gson.fromJson(uiServerResponse, JsonArray.class);
                if (serverArray == null || serverArray.size() == 0) {
                    logger.warn("emtry grpc server array, skip : {}", server);
                    continue;
                }
                return Lists.newArrayList(StreamSupport.stream(serverArray.spliterator(), false).map(f -> new Server(f.getAsString())).collect(Collectors.toList()));
            } catch (IOException e) {
                logger.error(e.getMessage(), e);
            }
        }
        logger.warn("none agentstream server return available grpc server.");
        return servers;
    }
}
