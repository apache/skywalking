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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static junit.framework.TestCase.assertTrue;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author kezhenxu94
 */
@RunWith(SpringJUnit4ClassRunner.class)
public class SampleVerificationITCase {
    private RestTemplate restTemplate = new RestTemplate();

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HHmm");

    @Test
    @DirtiesContext
    public void shouldGetCorrectTraces() {
        final String webappHost = System.getProperty("sw.webapp.host", "127.0.0.1");
        final String webappPort = System.getProperty("sw.webapp.port", "32794");
        final String url = "http://" + webappHost + ":" + webappPort + "/graphql";

        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime minutesAgo = now.minusMinutes(15);
        final String startTime = minutesAgo.format(TIME_FORMATTER);
        final String endTime = now.format(TIME_FORMATTER);
        String q = "{\"query\":\"query queryTraces($condition: TraceQueryCondition) {  traces: queryBasicTraces(condition: $condition) {    data: traces {      key: segmentId      endpointNames      duration      start      isError      traceIds    }    total  }}\",\"variables\":{\"condition\":{\"queryDuration\":{\"start\":\"" + startTime + "\",\"end\":\"" + endTime + "\",\"step\":\"MINUTE\"},\"traceState\":\"ALL\",\"paging\":{\"pageNum\":1,\"pageSize\":15,\"needTotal\":true},\"queryOrder\":\"BY_DURATION\"}}}";

        final ResponseEntity<GQLResponse<TracesData>> responseEntity =
            restTemplate.exchange(
                new RequestEntity<>(q, HttpMethod.POST, URI.create(url)),
                new ParameterizedTypeReference<GQLResponse<TracesData>>() {
                }
            );

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);

        final GQLResponse<TracesData> body = responseEntity.getBody();

        assertThat(body).isNotNull();

        assertTrue(
            body.getData()
                .getTraces()
                .getData()
                .stream()
                .anyMatch(trace -> trace.getEndpointNames().contains("/e2e/health-check"))
        );
    }
}
