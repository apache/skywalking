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

package test.apache.skywalking.apm.testcase.elasticsearch.controller;

import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.elasticsearch.action.admin.cluster.health.ClusterHealthResponse;
import org.elasticsearch.client.Client;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.common.xcontent.XContentFactory;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/case")
public class CaseController {

    private static final Logger LOGGER = LogManager.getLogger(CaseController.class);

    @Value("${elasticsearch.server}")
    private String host;

    @GetMapping("/healthcheck")
    public String healthcheck() throws Exception {
        Client client = initTransportClient();
        ClusterHealthResponse response = client.admin().cluster().prepareHealth().setWaitForYellowStatus().get();
        if (response.isTimedOut()) {
            String message = "elastic search node start fail!";
            LOGGER.error(message);
            throw new RuntimeException(message);
        }
        client.close();
        return "Success";
    }

    @GetMapping("/elasticsearch")
    public String elasticsearch() throws Exception {
        Client client = initTransportClient();
        String indexName = UUID.randomUUID().toString();
        try {
            // create
            index(client, indexName);
            // get
            get(client, indexName);
            // search
            search(client, indexName);
            // update
            update(client, indexName);
            // delete
            delete(client, indexName);
            // remove index
            client.admin().indices().prepareDelete(indexName).execute();
        } finally {
            if (null != client) {
                client.close();
            }
        }
        return "Success";
    }

    private void index(Client client, String indexName) throws IOException {
        try {
            client.prepareIndex(indexName, "test", "1")
                  .setSource(XContentFactory.jsonBuilder()
                                            .startObject()
                                            .field("name", "mysql innodb")
                                            .field("price", "0")
                                            .field("language", "chinese")
                                            .endObject())
                  .get();
        } catch (IOException e) {
            LOGGER.error("index document error.", e);
            throw e;
        }
    }

    private void get(Client client, String indexName) {
        client.prepareGet().setIndex(indexName).setId("1").execute();
    }

    private void update(Client client, String indexName) throws IOException {
        try {
            client.prepareUpdate(indexName, "test", "1")
                  .setDoc(XContentFactory.jsonBuilder().startObject().field("price", "9.9").endObject())
                  .execute();
        } catch (IOException e) {
            LOGGER.error("update document error.", e);
            throw e;
        }
    }

    private void delete(Client client, String indexName) {
        client.prepareDelete(indexName, "test", "1").execute();
    }

    private void search(Client client, String indexName) {
        client.prepareSearch(indexName).setTypes("test").setSize(10).execute();
    }

    private Client initTransportClient() throws UnknownHostException {
        TransportClient client = null;
        try {
            Settings settings = Settings.builder()
                                        .put("cluster.name", "docker-node")
                                        .put("client.transport.sniff", false)
                                        .build();

            client = new PreBuiltTransportClient(settings).addTransportAddress(new InetSocketTransportAddress(InetAddress
                .getByName(host), 9300));
        } catch (UnknownHostException e) {
            LOGGER.error("create client error", e);
            throw e;
        }
        return client;
    }
}

