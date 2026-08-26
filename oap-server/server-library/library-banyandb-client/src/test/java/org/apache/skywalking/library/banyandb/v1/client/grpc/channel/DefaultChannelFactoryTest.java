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

package org.apache.skywalking.library.banyandb.v1.client.grpc.channel;

import io.grpc.ManagedChannel;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import org.apache.skywalking.library.banyandb.v1.client.Options;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DefaultChannelFactoryTest {

    /**
     * TLS is switched on by the presence of the trust CA file, so a path that resolves to nothing would
     * otherwise be indistinguishable from TLS never having been configured, and the channel would quietly
     * stay plaintext.
     */
    @Test
    public void shouldRejectATrustCAPathThatIsMissing() {
        final FileNotFoundException e = assertThrows(
            FileNotFoundException.class, () -> newFactory("/definitely/not/a/real/path/ca.crt").create());

        assertTrue(e.getMessage().contains("/definitely/not/a/real/path/ca.crt"), e.getMessage());
    }

    @Test
    public void shouldRejectATrustCAPathThatIsADirectory() {
        assertThrows(
            FileNotFoundException.class, () -> newFactory(System.getProperty("java.io.tmpdir")).create());
    }

    /**
     * The unset path is the ordinary no-TLS deployment and must stay silent.
     */
    @Test
    public void shouldCreateAChannelWhenNoTrustCAIsConfigured() throws IOException {
        final ManagedChannel channel = newFactory("").create();
        try {
            assertNotNull(channel);
        } finally {
            channel.shutdownNow();
        }
    }

    private DefaultChannelFactory newFactory(String sslTrustCAPath) {
        final Options options = new Options();
        options.setSslTrustCAPath(sslTrustCAPath);
        return new DefaultChannelFactory(new URI[] {URI.create("//127.0.0.1:17912")}, options);
    }
}
