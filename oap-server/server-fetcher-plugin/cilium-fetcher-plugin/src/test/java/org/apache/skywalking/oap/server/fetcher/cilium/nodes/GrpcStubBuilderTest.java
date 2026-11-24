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

package org.apache.skywalking.oap.server.fetcher.cilium.nodes;

import com.linecorp.armeria.client.ClientBuilderParams;
import com.linecorp.armeria.client.ClientFactory;
import com.linecorp.armeria.client.Clients;
import io.cilium.api.peer.PeerGrpc;
import org.apache.skywalking.oap.server.fetcher.cilium.CiliumFetcherConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotSame;

public class GrpcStubBuilderTest {

    /**
     * A stub on Armeria's default factory connects without the configured TLS, so the fetcher
     * cannot reach a TLS Hubble endpoint.
     */
    @Test
    public void shouldBuildStubOnConfiguredFactory() {
        final GrpcStubBuilder builder = new GrpcStubBuilder(new CiliumFetcherConfig());

        final PeerGrpc.PeerBlockingStub stub = builder.buildClient("127.0.0.1", 4244, PeerGrpc.PeerBlockingStub.class);

        final ClientBuilderParams params = Clients.unwrap(stub, ClientBuilderParams.class);
        assertNotSame(ClientFactory.ofDefault(), params.options().factory());
    }
}
