/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.skywalking.library.kubernetes;

import com.sun.net.httpserver.HttpServer;
import io.fabric8.kubernetes.client.Config;
import io.fabric8.kubernetes.client.ConfigBuilder;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientBuilder;
import io.fabric8.kubernetes.client.KubernetesClientException;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class KubernetesApiReadsTest {
    private final AtomicLong millis = new AtomicLong(TimeUnit.HOURS.toMillis(1));
    private final List<String> errors = new ArrayList<>();
    private final FailureSummaryLogger failures = new FailureSummaryLogger(
        "Kubernetes API reads", "lookups keep what was read before, if anything", errors::add, line -> {
        }, millis::get, (task, delayMillis) -> {
            if (delayMillis == 0) {
                task.run();
            }
        });

    @Test
    public void aNotFoundResourceIsFindingNothingAndIsNotReported() {
        assertNull(KubernetesApiReads.read("list serviceentries", () -> {
            throw new KubernetesClientException("not found", 404, null);
        }, failures));
        summarize();
        assertEquals(List.of(), errors);
    }

    @Test
    public void aForbiddenReadFailsAndIsSummarized() {
        final KubernetesReadFailedException failure = assertThrows(
            KubernetesReadFailedException.class, () -> KubernetesApiReads.read("list nodes", () -> {
                throw new KubernetesClientException("forbidden", 403, null);
            }, failures));
        assertEquals("list nodes code=403", failure.getMessage());
        summarize();
        assertEquals(List.of(
            "Kubernetes API reads failed for 1 target in the last 1 minute, "
                + "lookups keep what was read before, if anything: list nodes code=403 count=1"), errors);
    }

    @Test
    public void aNotFoundResourceEndsAnEarlierFailureOfTheRead() {
        assertThrows(KubernetesReadFailedException.class, () -> KubernetesApiReads.read("list serviceentries", () -> {
            throw new KubernetesClientException("forbidden", 403, null);
        }, failures));
        KubernetesApiReads.read("list serviceentries", () -> {
            throw new KubernetesClientException("not found", 404, null);
        }, failures);
        summarize();
        assertEquals(List.of(), errors);
    }

    @Test
    public void aSuccessfulReadOfAnotherTargetDoesNotHideAFailure() {
        assertThrows(KubernetesReadFailedException.class, () -> KubernetesApiReads.read("get pods", () -> {
            throw new KubernetesClientException("forbidden", 403, null);
        }, failures));
        assertEquals("pod", KubernetesApiReads.read("list pods", () -> "pod", failures));
        summarize();
        assertEquals(1, errors.size());
    }

    @Test
    public void anUnavailableOrThrottlingApiServerFailsTheRead() {
        for (final int code : new int[] {500, 503, 429}) {
            final KubernetesReadFailedException failure = assertThrows(
                KubernetesReadFailedException.class, () -> KubernetesApiReads.read("list pods", () -> {
                    throw new KubernetesClientException("unavailable", code, null);
                }, failures));
            assertEquals("list pods code=" + code, failure.getMessage());
        }
    }

    @Test
    public void aReadFailingOtherwiseFailsToo() {
        final KubernetesReadFailedException failure = assertThrows(
            KubernetesReadFailedException.class, () -> KubernetesApiReads.read("list pods", () -> {
                throw new KubernetesClientException("failed", new ConnectException("Connection refused"));
            }, failures));
        assertEquals("list pods type=ConnectException", failure.getMessage());
    }

    @Test
    public void aFailingReadIsSentOnceSoItDoesNotHoldTheReader() throws Exception {
        final AtomicInteger requests = new AtomicInteger();
        final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/v1/pods", exchange -> {
            requests.incrementAndGet();
            exchange.sendResponseHeaders(503, -1);
            exchange.close();
        });
        server.start();
        final Config config = new ConfigBuilder(Config.empty())
            .withMasterUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/")
            .withRequestRetryBackoffLimit(10)
            .withRequestRetryBackoffInterval(10)
            .build();
        try (KubernetesClient client = new KubernetesClientBuilder().withConfig(config)
                                                                    .withHttpClientFactory(
                                                                        new KubernetesHttpClientFactory())
                                                                    .build()) {
            final KubernetesClientException failure = assertThrows(
                KubernetesClientException.class,
                () -> KubernetesApiReads.withoutRetries(client).pods().inAnyNamespace().list()
            );
            assertEquals(503, failure.getCode());
            assertEquals(1, requests.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void describesResponsesByStatusAndOtherFailuresByInnermostType() {
        assertEquals("code=403", KubernetesApiReads.describe(new KubernetesClientException("forbidden", 403, null)));
        assertEquals("type=ConnectException", KubernetesApiReads.describe(
            new KubernetesClientException("failed", new ConnectException("Connection refused"))));
        assertEquals("type=IllegalStateException", KubernetesApiReads.describe(new IllegalStateException()));
    }

    private void summarize() {
        millis.addAndGet(TimeUnit.MINUTES.toMillis(1));
        failures.logIfDue();
    }
}
