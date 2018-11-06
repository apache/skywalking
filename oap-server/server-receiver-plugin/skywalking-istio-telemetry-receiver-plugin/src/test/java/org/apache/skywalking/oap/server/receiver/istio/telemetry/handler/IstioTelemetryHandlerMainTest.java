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

package org.apache.skywalking.oap.server.receiver.istio.telemetry.handler;

import com.google.protobuf.TextFormat;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import io.istio.HandleMetricServiceGrpc;
import io.istio.IstioMetricProto;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class IstioTelemetryHandlerMainTest {

    public static void main(String[] args) throws InterruptedException {
        ManagedChannel channel = ManagedChannelBuilder.forAddress("localhost", 11800).usePlaintext(true).build();

        HandleMetricServiceGrpc.HandleMetricServiceBlockingStub stub = HandleMetricServiceGrpc.newBlockingStub(channel);

        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
        executor.schedule(() -> {
            try {
                send(stub);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }, 1, TimeUnit.SECONDS);
        Thread.sleep(5000L);
        executor.shutdown();
    }

    private static void send(final HandleMetricServiceGrpc.HandleMetricServiceBlockingStub  stub) throws IOException {
        for (String s : readData()) {
            IstioMetricProto.HandleMetricRequest.Builder requestBuilder = IstioMetricProto.HandleMetricRequest.newBuilder();
            try (InputStreamReader isr = new InputStreamReader(getResourceAsStream(String.format("fixture/%s", s)))) {
                TextFormat.getParser().merge(isr, requestBuilder);
            }
            stub.handleMetric(requestBuilder.build());
        }
    }

    private static Iterable<String> readData() throws IOException {
        Iterable<String> result = new LinkedList<>();
        try (
            InputStream in = getResourceAsStream("fixture");
            BufferedReader br = new BufferedReader(new InputStreamReader(in))) {
            String resource;

            while ((resource = br.readLine()) != null) {
                ((LinkedList<String>)result).add(resource);
            }
        }
        return result;
    }

    private static InputStream getResourceAsStream(final String resource) {
        final InputStream in = getContextClassLoader().getResourceAsStream(resource);
        return in == null ? IstioTelemetryHandlerMainTest.class.getResourceAsStream(resource) : in;
    }

    private static ClassLoader getContextClassLoader() {
        return Thread.currentThread().getContextClassLoader();
    }
}
