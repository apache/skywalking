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

import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestHighLevelClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @author aderm
 */
@Configuration
public class ElasticsearchConfig {

    @Value("${elasticsearch.server}")
    private String elasticsearchHost;

    @Bean(destroyMethod = "close")
    public RestHighLevelClient client() {
        HttpHost[] httpHostArry = parseEsHost();
        RestHighLevelClient client = new RestHighLevelClient((RestClient.builder(httpHostArry)));
        return client;
    }

    private HttpHost[] parseEsHost() {
        HttpHost[] httpHostArray = null;
        if (!elasticsearchHost.isEmpty()) {
            String[] hostIp = elasticsearchHost.split(",");
            httpHostArray = new HttpHost[hostIp.length];

            for (int i = 0; i < hostIp.length; ++i) {
                String[] hostIpItem = hostIp[i].split(":");
                String ip = hostIpItem[0].trim();
                String port = hostIpItem[1].trim();
                httpHostArray[i] = new HttpHost(ip, Integer.parseInt(port), "http");
            }
        }
        return httpHostArray;
    }
}
