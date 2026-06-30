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

package org.apache.skywalking.oap.server.library.server.http;

import com.linecorp.armeria.common.TlsKeyPair;
import io.netty.handler.ssl.util.SelfSignedCertificate;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HTTPServerTLSTest {

    @Test
    void shouldLoadKeyPairFromDisk(@TempDir Path dir) throws Exception {
        final SelfSignedCertificate cert = new SelfSignedCertificate("localhost");
        final Path keyPath = dir.resolve("server.key");
        final Path certPath = dir.resolve("server.crt");
        Files.copy(cert.privateKey().toPath(), keyPath);
        Files.copy(cert.certificate().toPath(), certPath);

        final TlsKeyPair keyPair =
            HTTPServer.loadTlsKeyPair(keyPath.toString(), certPath.toString());

        assertThat(keyPair.privateKey()).isNotNull();
        assertThat(keyPair.certificateChain()).isNotEmpty();
    }

    /**
     * The TLS provider re-invokes {@link HTTPServer#loadTlsKeyPair} on a schedule, so
     * overwriting the files in place (as happens when a Kubernetes secret is rotated)
     * must yield the new certificate on the next read.
     */
    @Test
    void shouldPickUpRotatedCertificate(@TempDir Path dir) throws Exception {
        final Path keyPath = dir.resolve("server.key");
        final Path certPath = dir.resolve("server.crt");

        final SelfSignedCertificate first = new SelfSignedCertificate("localhost");
        Files.copy(first.privateKey().toPath(), keyPath);
        Files.copy(first.certificate().toPath(), certPath);
        final TlsKeyPair before =
            HTTPServer.loadTlsKeyPair(keyPath.toString(), certPath.toString());

        // Rotate: overwrite the same paths with a freshly generated certificate.
        final SelfSignedCertificate second = new SelfSignedCertificate("localhost");
        Files.copy(second.privateKey().toPath(), keyPath, StandardCopyOption.REPLACE_EXISTING);
        Files.copy(second.certificate().toPath(), certPath, StandardCopyOption.REPLACE_EXISTING);
        final TlsKeyPair after =
            HTTPServer.loadTlsKeyPair(keyPath.toString(), certPath.toString());

        assertThat(after.certificateChain())
            .as("rotated certificate should be read back from disk")
            .isNotEqualTo(before.certificateChain());
    }

    @Test
    void shouldFailWhenFilesMissing() {
        assertThatThrownBy(() -> HTTPServer.loadTlsKeyPair("/no/such.key", "/no/such.crt"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
