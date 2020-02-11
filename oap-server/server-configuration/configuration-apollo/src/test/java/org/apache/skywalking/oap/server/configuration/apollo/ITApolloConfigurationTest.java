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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.Properties;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicResponseHandler;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.skywalking.apm.util.PropertyPlaceholderHelper;
import org.apache.skywalking.oap.server.library.module.ApplicationConfiguration;
import org.apache.skywalking.oap.server.library.module.ModuleManager;
import org.apache.skywalking.oap.server.library.util.CollectionUtils;
import org.apache.skywalking.oap.server.library.util.ResourceUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

public class ITApolloConfigurationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(ITApolloConfigurationTest.class);

    private final Yaml yaml = new Yaml();
    private final String token = "f71f002a4ff9845639ef655ee7019759e31449de";
    private final CloseableHttpClient httpClient = HttpClients.createDefault();
    private final ResponseHandler responseHandler = new BasicResponseHandler();

    private String baseUrl;
    private ApolloConfigurationTestProvider provider;

    @Before
    public void setUp() throws Exception {
        String host = System.getProperty("apollo.portal.host");
        String port = System.getProperty("apollo.portal.port");

        baseUrl = "http://" + host + ":" + port;
        LOGGER.info("baseUrl: {}", baseUrl);

        final ApplicationConfiguration applicationConfiguration = new ApplicationConfiguration();
        loadConfig(applicationConfiguration);

        final ModuleManager moduleManager = new ModuleManager();
        moduleManager.init(applicationConfiguration);

        provider = (ApolloConfigurationTestProvider) moduleManager.find(ApolloConfigurationTestModule.NAME).provider();

        assertNotNull(provider);
    }

    @SuppressWarnings("StatementWithEmptyBody")
    @Ignore // due to instability
    @Test(timeout = 10000)
    public void shouldReadUpdated() {
        try {
            assertNull(provider.watcher.value());

            final HttpPost createConfigPost = new HttpPost(baseUrl + "/openapi/v1/envs/DEV" + "/apps/SampleApp" + "/clusters/default" + "/namespaces/application" + "/items");
            createConfigPost.setHeader("Authorization", token);
            createConfigPost.setHeader("Content-Type", "application/json;charset=UTF-8");
            final StringEntity entity = new StringEntity("{\n" + "    \"key\":\"test-module.default.testKey\",\n" + "    \"value\":\"3000\",\n" + "    \"comment\":\"test key\",\n" + "    \"dataChangeCreatedBy\":\"apollo\"\n" + "}");
            createConfigPost.setEntity(entity);
            final String createResponse = (String) httpClient.execute(createConfigPost, responseHandler);
            LOGGER.info("createResponse: {}", createResponse);

            final HttpPost releaseConfigRequest = new HttpPost(baseUrl + "/openapi/v1/envs/DEV" + "/apps/SampleApp" + "/clusters/default" + "/namespaces/application/releases");
            releaseConfigRequest.setEntity(new StringEntity("{\n" + "    \"releaseTitle\":\"2019-06-07\",\n" + "    \"releaseComment\":\"test\",\n" + "    \"releasedBy\":\"apollo\"\n" + "}"));
            releaseConfigRequest.setHeader("Authorization", token);
            releaseConfigRequest.setHeader("Content-Type", "application/json;charset=UTF-8");
            final String releaseCreateResponse = (String) httpClient.execute(releaseConfigRequest, responseHandler);
            LOGGER.info("releaseCreateResponse: {}", releaseCreateResponse);

            for (String v = provider.watcher.value(); v == null; v = provider.watcher.value()) {
            }

            assertEquals("3000", provider.watcher.value());

            final HttpDelete deleteConfigRequest = new HttpDelete(baseUrl + "/openapi/v1" + "/envs/DEV" + "/apps/SampleApp" + "/clusters/default" + "/namespaces/application" + "/items/test-module.default.testKey" + "?operator=apollo");
            deleteConfigRequest.setHeader("Authorization", token);
            deleteConfigRequest.setHeader("Content-Type", "application/json;charset=UTF-8");
            httpClient.execute(deleteConfigRequest);
            final String releaseDeleteResponse = (String) httpClient.execute(releaseConfigRequest, responseHandler);
            LOGGER.info("releaseDeleteResponse: {}", releaseDeleteResponse);

            for (String v = provider.watcher.value(); v != null; v = provider.watcher.value()) {
            }

            assertNull(provider.watcher.value());
        } catch (IOException e) {
            LOGGER.error(e.getMessage(), e);
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

    @After
    public void cleanUp() throws IOException {
        try {
            final HttpDelete deleteConfigRequest = new HttpDelete(baseUrl + "/openapi/v1" + "/envs/DEV" + "/apps/SampleApp" + "/clusters/default" + "/namespaces/application" + "/items/test-module.default.testKey" + "?operator=apollo");
            deleteConfigRequest.setHeader("Authorization", token);
            deleteConfigRequest.setHeader("Content-Type", "application/json;charset=UTF-8");
            httpClient.execute(deleteConfigRequest);

            final HttpPost releaseConfigRequest = new HttpPost(baseUrl + "/openapi/v1/envs/DEV" + "/apps/SampleApp" + "/clusters/default" + "/namespaces/application/releases");
            releaseConfigRequest.setEntity(new StringEntity("{\n" + "    \"releaseTitle\":\"2019-06-07\",\n" + "    \"releaseComment\":\"test\",\n" + "    \"releasedBy\":\"apollo\"\n" + "}"));
            releaseConfigRequest.setHeader("Authorization", token);
            releaseConfigRequest.setHeader("Content-Type", "application/json;charset=UTF-8");
            httpClient.execute(releaseConfigRequest, responseHandler);
        } catch (Exception e) {
            LOGGER.error(e.getMessage(), e);
        }
    }
}
