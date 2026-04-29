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

package org.apache.skywalking.oap.server.library.it;

/**
 * Single source of truth for the BanyanDB container image + port configuration used by
 * integration tests across the repository.
 *
 * <p>The tag comes from {@code test/e2e-v2/script/env}'s {@code SW_BANYANDB_COMMIT}, read
 * via {@link ITVersions}. Every IT that wants a BanyanDB instance should consult this class
 * so a version bump lands in one place — the env file — and every IT picks it up on the
 * next rebuild. Hardcoding a tag in a test file drifts silently, which is why the {@code
 * docker/.env} standalone file is pinned to the same commit the env file carries.
 *
 * <p>This class deliberately does NOT return a {@code GenericContainer} directly. Testcontainers
 * is a test-scope dependency throughout the build, and returning a Testcontainers type from a
 * {@code library-integration-test} main-compile helper would force every downstream test module
 * that just wants the image name to also pull Testcontainers at compile scope. Instead, tests
 * call {@link #image()} and {@link #standaloneCommand()} and wire their own
 * {@code GenericContainer} — keeping Testcontainers scope as-is.
 */
public final class BanyanDBTestContainer {

    public static final int GRPC_PORT = 17912;
    public static final int HTTP_PORT = 17913;

    /** HTTP path Testcontainers' Wait strategies can probe to know the server is ready. */
    public static final String HEALTH_ENDPOINT = "/api/healthz";

    private static final String REGISTRY = "ghcr.io";
    private static final String IMAGE_NAME = "apache/skywalking-banyandb";

    private BanyanDBTestContainer() {
    }

    /**
     * Fully-qualified image reference pinned to the repo's currently-declared BanyanDB commit.
     * Tests use this with {@code DockerImageName.parse(...)} to construct their
     * {@code GenericContainer}.
     */
    public static String image() {
        final String tag = ITVersions.get("SW_BANYANDB_COMMIT");
        if (tag == null || tag.isEmpty()) {
            throw new IllegalStateException(
                "SW_BANYANDB_COMMIT missing from test/e2e-v2/script/env — "
                    + "cannot determine BanyanDB image tag for integration tests");
        }
        return REGISTRY + "/" + IMAGE_NAME + ":" + tag;
    }

    /**
     * Standalone-mode arguments matching what every BanyanDB IT in the repo has used
     * historically. Tests pass the returned array through
     * {@code GenericContainer.withCommand(...)} so stream and measure data land on a
     * predictable in-container path for any follow-up diagnostics.
     */
    public static String[] standaloneCommand() {
        return new String[] {
            "standalone",
            "--stream-root-path", "/tmp/banyandb-stream-data",
            "--measure-root-path", "/tmp/banyand-measure-data",
            // Drive the testing-only metadata cache wait flags down to 1s so a
            // delete-measure + define cycle isn't silently absorbed by stale resolver
            // cache (otherwise post-recreate writes target the old internal measure id
            // and disappear).
            "--measure-metadata-cache-wait-duration", "1s",
            "--stream-metadata-cache-wait-duration", "1s",
            "--trace-metadata-cache-wait-duration", "1s",
            // Flush every 100ms so awaitDataPoints sees writes promptly. Production
            // defaults are 5s for measure / property / schema-server and 1s for stream
            // / trace, tuned for throughput; tests prefer end-to-end latency.
            "--measure-flush-timeout", "100ms",
            "--stream-flush-timeout", "100ms",
            "--trace-flush-timeout", "100ms",
            "--property-flush-timeout", "100ms",
        };
    }
}
