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

package org.apache.skywalking.oap.server.configuration.apollo;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.PropertyPlaceholderHelper;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.testcontainers.containers.DockerComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.time.Duration;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;

@Slf4j
@Testcontainers
public class ITApolloConfigurationTest {
    private final Yaml yaml = new Yaml();
    private final String token = "f71f002a4ff9845639ef655ee7019759e31449de";
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private final ResponseHandler responseHandler = new BasicResponseHandler();

    private String baseUrl;
    private ApolloConfigurationTestProvider provider;

    @Container
    public final static DockerComposeContainer<?> ENVIRONMENT =
        new DockerComposeContainer<>(new File(ITApolloConfigurationTest.class
                                                .getClassLoader()
                                                .getResource("docker/docker-compose.yaml").getPath()))
            .withExposedService("apollo-config-and-portal", 8080,
                                Wait.forLogMessage(".*Config service started.*", 1))
            .withExposedService("apollo-config-and-portal", 8070,
                                Wait.forLogMessage(".*Portal started. You can visit.*", 1)
                                    .withStartupTimeout(Duration.ofSeconds(100))
            );

    @BeforeEach
    public void setUp() throws Exception {
        String metaHost = ENVIRONMENT.getServiceHost("apollo-config-and-portal", 8080);
        String metaPort = ENVIRONMENT.getServicePort("apollo-config-and-portal", 8080).toString();
        System.setProperty("apollo.configService", "http://" + metaHost + ":" + metaPort);
        System.setProperty("apollo.meta.port", metaPort);
        System.setProperty("apollo.meta.host", metaHost);
        log.info("apollo.configService: {}", System.getProperty("apollo.configService"));

        String host = ENVIRONMENT.getServiceHost("apollo-config-and-portal", 8070);
        String port = ENVIRONMENT.getServicePort("apollo-config-and-portal", 8070).toString();
        baseUrl = "http://" + host + ":" + port;
        log.info("baseUrl: {}", baseUrl);

        final ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        loadConfig(applicationConfiguration);

        final ModuleManager moduleManager = new ModuleManager();
        moduleManager.init(applicationConfiguration);

        provider = (ApolloConfigurationTestProvider) moduleManager.find(ApolloConfigurationTestModule.NAME).provider();

        assertNotNull(provider);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Test
    @Timeout(100)
    public void shouldReadUpdated() {
        try {
            assertNull(provider.watcher.value());

            final HttpPost createConfigPost = new HttpPost(baseUrl + "/openapi/v1/envs/DEV" + "/apps/SampleApp" + "/clusters/default" + "/namespaces/application" + "/items");
            createConfigPost.setHeader("Authorization", token);
            createConfigPost.setHeader("Content-Type", "application/json;charset=UTF-8");
            final StringEntity entity = new StringEntity("{\n" + "    \"key\":\"test-module.default.testKey\",\n" + "    \"value\":\"3000\",\n" + "    \"comment\":\"test key\",\n" + "    \"dataChangeCreatedBy\":\"apollo\"\n" + "}");
            createConfigPost.setEntity(entity);
            String createResponse = null;
            //retry to wait apollo adminserver registered
            for (int r = 1; r <= 10 && createResponse == null; r++) {
                TimeUnit.SECONDS.sleep(5);
                log.info("try createItem, times...: {}", r);
                createResponse = this.httpExec(createConfigPost, responseHandler);
                log.info("createResponse: {}", createResponse);
            }

            final HttpPost releaseConfigRequest = new HttpPost(baseUrl + "/openapi/v1/envs/DEV" + "/apps/SampleApp" + "/clusters/default" + "/namespaces/application/releases");
            releaseConfigRequest.setEntity(new StringEntity("{\n" + "    \"releaseTitle\":\"2019-06-07\",\n" + "    \"releaseComment\":\"test\",\n" + "    \"releasedBy\":\"apollo\"\n" + "}"));
            releaseConfigRequest.setHeader("Authorization", token);
            releaseConfigRequest.setHeader("Content-Type", "application/json;charset=UTF-8");
            final String releaseCreateResponse = (String) httpClient.execute(releaseConfigRequest, responseHandler);
            log.info("releaseCreateResponse: {}", releaseCreateResponse);

            for (String v = provider.watcher.value(); v == null; v = provider.watcher.value()) {
            }

            assertEquals("3000", provider.watcher.value());

            final HttpDelete deleteConfigRequest = new HttpDelete(baseUrl + "/openapi/v1" + "/envs/DEV" + "/apps/SampleApp" + "/clusters/default" + "/namespaces/application" + "/items/test-module.default.testKey" + "?operator=apollo");
            deleteConfigRequest.setHeader("Authorization", token);
            deleteConfigRequest.setHeader("Content-Type", "application/json;charset=UTF-8");
            httpClient.execute(deleteConfigRequest);
            final String releaseDeleteResponse = (String) httpClient.execute(releaseConfigRequest, responseHandler);
            log.info("releaseDeleteResponse: {}", releaseDeleteResponse);

            for (String v = provider.watcher.value(); v != null; v = provider.watcher.value()) {
            }

            assertNull(provider.watcher.value());
        } catch (IOException | InterruptedException e) {
            log.error(e.getMessage(), e);
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Test
    @Timeout(100)
    public void shouldReadUpdated4Group() {
        try {
            assertEquals("{}", provider.groupWatcher.groupItems().toString());

            final HttpPost createConfigPost = new HttpPost(baseUrl + "/openapi/v1/envs/DEV" + "/apps/SampleApp" + "/clusters/default" + "/namespaces/application" + "/items");
            createConfigPost.setHeader("Authorization", token);
            createConfigPost.setHeader("Content-Type", "application/json;charset=UTF-8");
            final StringEntity entityItem1 = new StringEntity("{\n" + "    \"key\":\"test-module.default.testKeyGroup.item1\",\n" + "    \"value\":\"100\",\n" + "    \"comment\":\"test key\",\n" + "    \"dataChangeCreatedBy\":\"apollo\"\n" + "}");
            createConfigPost.setEntity(entityItem1);

            String createResponseItem1 = null;
            //retry to wait apollo adminserver registered
            for (int r = 1; r <= 10 && createResponseItem1 == null; r++) {
                TimeUnit.SECONDS.sleep(5);
                log.info("try createItem, times...: {}", r);
                createResponseItem1 = this.httpExec(createConfigPost, responseHandler);
                log.info("createResponse: {}", createResponseItem1);
            }

            final StringEntity entityItem2 = new StringEntity("{\n" + "    \"key\":\"test-module.default.testKeyGroup.item2\",\n" + "    \"value\":\"200\",\n" + "    \"comment\":\"test key\",\n" + "    \"dataChangeCreatedBy\":\"apollo\"\n" + "}");
            createConfigPost.setEntity(entityItem2);
            final String createResponseItem2 = (String) httpClient.execute(createConfigPost, responseHandler);
            log.info("createResponseItem2: {}", createResponseItem2);

            final HttpPost releaseConfigRequest = new HttpPost(baseUrl + "/openapi/v1/envs/DEV" + "/apps/SampleApp" + "/clusters/default" + "/namespaces/application/releases");
            releaseConfigRequest.setEntity(new StringEntity("{\n" + "    \"releaseTitle\":\"2019-06-07\",\n" + "    \"releaseComment\":\"test\",\n" + "    \"releasedBy\":\"apollo\"\n" + "}"));
            releaseConfigRequest.setHeader("Authorization", token);
            releaseConfigRequest.setHeader("Content-Type", "application/json;charset=UTF-8");
            final String releaseCreateResponse = (String) httpClient.execute(releaseConfigRequest, responseHandler);
            log.info("releaseCreateResponse: {}", releaseCreateResponse);

            for (String v = provider.groupWatcher.groupItems().get("item1"); v == null; v = provider.groupWatcher.groupItems().get("item1")) {
            }
            for (String v = provider.groupWatcher.groupItems().get("item2"); v == null; v = provider.groupWatcher.groupItems().get("item2")) {
            }
            assertEquals("100", provider.groupWatcher.groupItems().get("item1"));
            assertEquals("200", provider.groupWatcher.groupItems().get("item2"));

            //test remove item1
            final HttpDelete deleteConfigRequest = new HttpDelete(baseUrl + "/openapi/v1" + "/envs/DEV" + "/apps/SampleApp" + "/clusters/default" + "/namespaces/application" + "/items/test-module.default.testKeyGroup.item1" + "?operator=apollo");
            deleteConfigRequest.setHeader("Authorization", token);
            deleteConfigRequest.setHeader("Content-Type", "application/json;charset=UTF-8");
            httpClient.execute(deleteConfigRequest);

            final String releaseDeleteResponse = (String) httpClient.execute(releaseConfigRequest, responseHandler);
            log.info("releaseDeleteResponse: {}", releaseDeleteResponse);
            for (String v = provider.groupWatcher.groupItems().get("item1"); v != null; v = provider.groupWatcher.groupItems().get("item1")) {
            }
            assertNull(provider.groupWatcher.groupItems().get("item1"));
            assertEquals("200", provider.groupWatcher.groupItems().get("item2"));
        } catch (IOException | InterruptedException e) {
            log.error(e.getMessage(), e);
            fail(e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private void loadConfig(ApplicationConfiguration configuration) throws FileNotFoundException {
        Reader applicationReader = ResourceUtils.read("application.yml");
        Map<String, Map<String, Map<String, ?>>> moduleConfig = yaml.loadAs(applicationReader, Map.class);
        if (CollectionUtils.isNotEmpty(moduleConfig)) {
            moduleConfig.forEach((moduleName, providerConfig) -> {
                if (providerConfig.size() > 0) {
                    ApplicationConfiguration.ModuleConfiguration moduleConfiguration = configuration.addModule(moduleName);
                    providerConfig.forEach((name, propertiesConfig) -> {
                        Properties properties = new Properties();
                        if (propertiesConfig != null) {
                            propertiesConfig.forEach((key, value) -> {
                                properties.put(key, value);
                                final Object replaceValue = yaml.load(PropertyPlaceholderHelper.INSTANCE.replacePlaceholders(value + "", properties));
                                if (replaceValue != null) {
                                    properties.replace(key, replaceValue);
                                }
                            });
                        }
                        moduleConfiguration.addProviderConfiguration(name, properties);
                    });
                }
            });
        }
    }

    //for retry
    private String httpExec(HttpUriRequest request, ResponseHandler responseHandler) {
        try {
            return (String) this.httpClient.execute(request, responseHandler);
        } catch (IOException ignored) {
            return null;
        }
    }
}
