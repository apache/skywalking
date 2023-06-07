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
import org.apache.skywalking.oap.server.ai.pipeline.grpc.HttpUriRecognitionServiceGrpc;
import org.apache.skywalking.oap.server.ai.pipeline.services.api.HttpUriPattern;
import org.apache.skywalking.oap.server.ai.pipeline.services.api.HttpUriRecognition;
import org.apache.skywalking.oap.server.library.client.grpc.GRPCClient;

public class HttpUriRecognitionService implements HttpUriRecognition {
    public HttpUriRecognitionService(String addr, int port) {
        GRPCClient client = new GRPCClient(addr, port);
        client.connect();
        ManagedChannel channel = client.getChannel();
        HttpUriRecognitionServiceGrpc.HttpUriRecognitionServiceBlockingStub stub = HttpUriRecognitionServiceGrpc.newBlockingStub(channel);
    }

    @Override
    public List<HttpUriPattern> fetchAllPatterns(final String service) {
        return null;
    }

    @Override
    public void recognize(final String service, final List<HTTPUri> unrecognizedURIs, final Callback callback) {

    }
}
