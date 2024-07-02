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
import com.linecorp.armeria.client.ClientFactoryBuilder;
import com.linecorp.armeria.client.ClientOptions;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.skywalking.oap.server.fetcher.cilium.CiliumFetcherConfig;

import java.io.File;
import java.net.URI;

/**
 * For Building all the gRPC stubs for the cilium fetcher.
 */
@Slf4j
public class GrpcStubBuilder implements ClientBuilder {
    private final CiliumFetcherConfig config;
    private final ClientFactory clientFactory;

    public GrpcStubBuilder(CiliumFetcherConfig config) {
        final ClientFactoryBuilder builder = ClientFactory.builder();
        if (config.isSslConnection()) {
            builder
                .tlsNoVerify()  // skip the verification of the server's certificate(for the host checker)
                .useHttp2WithoutAlpn(true) // use HTTP/2 without ALPN(cilium not support for now)
                .tlsCustomizer(ctx -> {
                    ctx.keyManager(new File(config.getSslCertChainFile()), new File(config.getSslPrivateKeyFile()));
                    ctx.trustManager(new File(config.getSslCaFile()));
            });
        }

        this.config = config;
        this.clientFactory = builder.build();
    }

    @Override
    @SneakyThrows
    public <T> T buildClient(String host, int port, Class<T> stubClass) {
        String proto = "http";
        if (config.isSslConnection()) {
            proto = "https";
        }

        final URI url = new URI("gproto+" + proto, null, host, port, "/", null, null);
        return (T) clientFactory.newClient(ClientBuilderParams.of(url, stubClass, ClientOptions.of(
            ClientOptions.RESPONSE_TIMEOUT_MILLIS.newValue(Long.MAX_VALUE), // For the cilium fetcher, we need to wait for the response(all the data from streaming)
            ClientOptions.MAX_RESPONSE_LENGTH.newValue(0L)  // For the cilium streaming fetcher, we should ignore the response length limit
        )));
    }
}
