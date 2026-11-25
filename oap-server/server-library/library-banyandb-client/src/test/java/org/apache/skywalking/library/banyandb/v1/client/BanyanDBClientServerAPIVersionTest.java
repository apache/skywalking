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

package org.apache.skywalking.library.banyandb.v1.client;

import io.grpc.stub.StreamObserver;
import java.io.IOException;
import org.apache.skywalking.banyandb.common.v1.BanyandbCommon;
import org.apache.skywalking.banyandb.common.v1.ServiceGrpc;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class BanyanDBClientServerAPIVersionTest extends AbstractBanyanDBClientTest {
    private static final String UNSUPPORTED = "0.0";
    private static final String OLDEST = BanyanDBAPIVersion.oldest().getVersion();
    private static final String NEWEST =
        BanyanDBAPIVersion.values()[BanyanDBAPIVersion.values().length - 1].getVersion();

    private String reportedVersion;

    @BeforeEach
    public void setUp() throws IOException {
        super.setUp(bindService(new ServiceGrpc.ServiceImplBase() {
            @Override
            public void getAPIVersion(final BanyandbCommon.GetAPIVersionRequest request,
                                      final StreamObserver<BanyandbCommon.GetAPIVersionResponse> responseObserver) {
                responseObserver.onNext(BanyandbCommon.GetAPIVersionResponse.newBuilder()
                    .setVersion(BanyandbCommon.APIVersion.newBuilder()
                        .setVersion(reportedVersion)
                        .setRevision("test-revision"))
                    .build());
                responseObserver.onCompleted();
            }
        }));
    }

    @AfterEach
    public void tearDown() {
        if (this.channel != null) {
            this.channel.shutdownNow();
        }
    }

    @Test
    public void supportedServerVersionIsRecorded() throws Exception {
        reportedVersion = OLDEST;

        final BanyandbCommon.APIVersion reported = client.verifyServerAPIVersion(null);

        assertEquals(OLDEST, reported.getVersion());
        assertEquals("test-revision", reported.getRevision());
        assertEquals(OLDEST, client.getServerAPIVersion());
    }

    @Test
    public void unsupportedServerVersionFails() {
        reportedVersion = UNSUPPORTED;

        final IllegalStateException e =
            assertThrows(IllegalStateException.class, () -> client.verifyServerAPIVersion(""));

        assertTrue(e.getMessage().contains("server API version: " + UNSUPPORTED), e.getMessage());
        assertNull(client.getServerAPIVersion());
    }

    @Test
    public void forcedVersionReplacesTheReportedOne() throws Exception {
        reportedVersion = OLDEST;

        final BanyandbCommon.APIVersion reported = client.verifyServerAPIVersion(NEWEST);

        assertEquals(OLDEST, reported.getVersion());
        assertEquals(NEWEST, client.getServerAPIVersion());
    }

    @Test
    public void forcedVersionMustBeSupported() {
        reportedVersion = OLDEST;

        final IllegalStateException e =
            assertThrows(IllegalStateException.class, () -> client.verifyServerAPIVersion(UNSUPPORTED));

        assertTrue(e.getMessage().contains("forced in place of the server's " + OLDEST), e.getMessage());
        assertNull(client.getServerAPIVersion());
    }
}
