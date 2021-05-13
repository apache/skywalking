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

package org.apache.skywalking.apm.testcase.elasticsearch.config;

import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.TransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.InetAddress;

@Configuration
public class TransportClientConfig {

    @Value("${elasticsearch.server}")
    private String elasticsearchHost;

    public final static Integer PORT = 9300; //port

    @Bean
    public TransportClient getESClientConnection()
        throws Exception {

        TransportClient client = null;
        Settings settings = Settings.builder()
            .put("cluster.name", "docker-node")
            .put("client.transport.sniff", false)
            .build();

        client = new PreBuiltTransportClient(settings);
        for (TransportAddress i : parseEsHost()) {
            client.addTransportAddress(i);
        }
        return client;
    }

    private TransportAddress[] parseEsHost()
        throws Exception {
        TransportAddress[] transportAddresses = null;
        if (!elasticsearchHost.isEmpty()) {
            String[] hostIp = elasticsearchHost.split(",");
            transportAddresses = new TransportAddress[hostIp.length];

            for (int i = 0; i < hostIp.length; ++i) {
                String[] hostIpItem = hostIp[i].split(":");
                String ip = hostIpItem[0].trim();
                String port = hostIpItem[1].trim();
                transportAddresses[i] = new TransportAddress(InetAddress.getByName(ip), PORT);
            }
        }
        return transportAddresses;
    }
}
