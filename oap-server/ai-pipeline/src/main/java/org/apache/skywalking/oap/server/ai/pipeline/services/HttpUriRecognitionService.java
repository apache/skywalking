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

package org.apache.skywalking.oap.server.ai.pipeline.services;

import io.grpc.ManagedChannel;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.ai.pipeline.grpc.HttpRawUri;
import org.apache.skywalking.oap.server.ai.pipeline.grpc.HttpUriRecognitionRequest;
import org.apache.skywalking.oap.server.ai.pipeline.grpc.HttpUriRecognitionResponse;
import org.apache.skywalking.oap.server.ai.pipeline.grpc.HttpUriRecognitionServiceGrpc;
import org.apache.skywalking.oap.server.ai.pipeline.grpc.HttpUriRecognitionSyncRequest;
import org.apache.skywalking.oap.server.ai.pipeline.services.api.HttpUriPattern;
import org.apache.skywalking.oap.server.ai.pipeline.services.api.HttpUriRecognition;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;
import org.apache.skywalking.oap.server.library.util.StringUtil;

@Slf4j
public class HttpUriRecognitionService implements HttpUriRecognition {
    private HttpUriRecognitionServiceGrpc.HttpUriRecognitionServiceBlockingStub stub;
    private String version = "NULL";

    public HttpUriRecognitionService(String addr, int port) {
        if (StringUtil.isEmpty(addr) || port <= 0) {
            return;
        }
        GRPCClient client = new GRPCClient(addr, port);
        client.connect();
        ManagedChannel channel = client.getChannel();
        stub = HttpUriRecognitionServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public boolean isInitialized() {
        return stub != null;
    }

    @Override
    public List<HttpUriPattern> fetchAllPatterns(final String service) {
        try {
            if (stub == null) {
                return null;
            }
            final HttpUriRecognitionResponse httpUriRecognitionResponse
                = stub.withDeadlineAfter(30, TimeUnit.SECONDS)
                      .fetchAllPatterns(
                          HttpUriRecognitionSyncRequest.newBuilder()
                                                       .setService(service)
                                                       .setVersion(version)
                                                       .build()
                      );
            final String newVersion = httpUriRecognitionResponse.getVersion();
            if (version.equals(newVersion)) {
                // Same version, nothing changed.
                return null;
            }
            return httpUriRecognitionResponse.getPatternsList()
                                             .stream()
                                             .map(pattern -> new HttpUriPattern(pattern.getPattern()))
                                             .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("fetch all patterns failed from remote server.", e);
            return null;
        }
    }

    @Override
    public void feedRawData(final String service, final List<HTTPUri> unrecognizedURIs) {
        try {
            if (stub == null) {
                return;
            }
            final HttpUriRecognitionRequest.Builder builder = HttpUriRecognitionRequest.newBuilder();
            builder.setService(service);
            unrecognizedURIs.forEach(httpUri -> {
                builder.getUnrecognizedURIsBuilderList().add(
                    HttpRawUri.newBuilder().setName(httpUri.getName()).setMatchedCounter(httpUri.getMatchedCounter())
                );
            });
            stub.withDeadlineAfter(30, TimeUnit.SECONDS)
                .feedRawData(builder.build());
        } catch (Exception e) {
            log.error("feed matched and unmatched URIs to the remote server.", e);
        }
    }
}
