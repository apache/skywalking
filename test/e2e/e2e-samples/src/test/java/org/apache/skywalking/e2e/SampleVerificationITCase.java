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

package org.apache.skywalking.e2e;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kezhenxu94
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class SampleVerificationITCase {
    private final RestTemplate restTemplate = new RestTemplate();

    private SimpleQueryClient client;

    @Before
    public void setUp() {
        final String webappHost = System.getProperty("sw.webapp.host", "127.0.0.1");
        final String webappPort = System.getProperty("sw.webapp.port", "32829");
        final String url = "http://" + webappHost + ":" + webappPort + "/graphql";
        client = new SimpleQueryClient(url);
    }

    @Test
    @DirtiesContext
    public void shouldGetCorrectTraces() throws Exception {
        final LocalDateTime minutesAgo = LocalDateTime.now(ZoneOffset.UTC);

        final String clientUrl = "http://" + System.getProperty("client.host", "127.0.0.1") + ":" + System.getProperty("client.port", "32830");
        final Map<String, String> user = new HashMap<>();
        user.put("name", "SkyWalking");
        final ResponseEntity<String> responseEntity = restTemplate.postForEntity(
            clientUrl + "/e2e/users",
            user,
            String.class
        );
        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);

        Thread.sleep(5000);

        final LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);

        final List<Trace> traces = client.traces(
            new TracesQuery()
                .start(minutesAgo)
                .end(now)
                .orderByDuration()
        );

        final InputStream expectedInputStream =
            new ClassPathResource("expected-data/org.apache.skywalking.e2e.SampleVerificationITCase.shouldGetCorrectTraces.yml").getInputStream();

        final TracesMatcher tracesMatcher = new Yaml().loadAs(expectedInputStream, TracesMatcher.class);
        tracesMatcher.verify(traces);
    }
}
