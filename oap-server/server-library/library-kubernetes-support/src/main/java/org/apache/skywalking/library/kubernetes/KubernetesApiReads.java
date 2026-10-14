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

import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.KubernetesClientException;
import io.fabric8.kubernetes.client.RequestConfigBuilder;
import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

/**
 * Reads from the Kubernetes API server for the lookups, and summarizes the failed reads instead of logging each.
 */
@Slf4j
public final class KubernetesApiReads {
    private static final int NOT_FOUND = 404;
    private static final FailureSummaryLogger FAILURES = new FailureSummaryLogger(
        "Kubernetes API reads",
        "lookups keep what was read before, if anything, and retry the failed reads after the refresh period",
        log::error, log::info);

    private KubernetesApiReads() {
    }

    /**
     * @return the client the reads use: the shared client without request retries, so a failing read holds the one
     * reader for a single attempt instead of through fabric8's retries and backoff; when the key is read again follows
     * the rules of {@link LookupCache}
     */
    public static KubernetesClient client() {
        return ClientWithoutRetries.INSTANCE;
    }

    /**
     * @return a copy of {@code client} sharing its connections and threads, whose requests are not retried
     */
    static KubernetesClient withoutRetries(final KubernetesClient client) {
        return client.newClient(new RequestConfigBuilder(client.getConfiguration().getRequestConfig())
                                    .withRequestRetryBackoffLimit(0)
                                    .build())
                     .adapt(KubernetesClient.class);
    }

    /**
     * @param target what is read, such as {@code list pods}, naming it in the summary of failed reads
     * @param read   the read
     * @return what {@code read} returned; null for a 404, the API server not serving the resource, such as Istio
     * ServiceEntries without Istio, which finds nothing and is not reported
     * @throws KubernetesReadFailedException when the read failed otherwise, such as for a 403, a connection failure or
     *                                       a timeout; the failure is summarized as ERROR
     */
    public static <T> T read(final String target, final Supplier<T> read) {
        return read(target, read, FAILURES);
    }

    static <T> T read(final String target, final Supplier<T> read, final FailureSummaryLogger failures) {
        final T value;
        try {
            value = read.get();
        } catch (final KubernetesClientException e) {
            if (e.getCode() == NOT_FOUND) {
                // Finding nothing, like a read that succeeds: it ends an earlier failure of the target.
                failures.recordSuccess(target);
                return null;
            }
            throw failed(target, e, failures);
        } catch (final RuntimeException | LinkageError e) {
            throw failed(target, e, failures);
        }
        failures.recordSuccess(target);
        return value;
    }

    private static KubernetesReadFailedException failed(final String target, final Throwable failure,
                                                        final FailureSummaryLogger failures) {
        final String description = describe(failure);
        failures.recordFailure(target, description);
        return new KubernetesReadFailedException(target, description);
    }

    /**
     * @return {@code code=<HTTP status>} for a response the API server sent, otherwise the type of the innermost
     * cause, such as {@code type=ConnectException}
     */
    static String describe(final Throwable failure) {
        Throwable innermost = failure;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof KubernetesClientException && ((KubernetesClientException) cause).getCode() > 0) {
                return "code=" + ((KubernetesClientException) cause).getCode();
            }
            innermost = cause;
            if (cause.getCause() == cause) {
                break;
            }
        }
        return "type=" + innermost.getClass().getSimpleName();
    }

    private static final class ClientWithoutRetries {
        private static final KubernetesClient INSTANCE = withoutRetries(SharedKubernetesClient.INSTANCE.get());
    }
}
