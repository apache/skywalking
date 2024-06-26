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

package org.apache.skywalking.oap.server.library.server.grpc.ssl;

import io.grpc.netty.GrpcSslContexts;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import javax.net.ssl.SSLException;
import org.apache.skywalking.oap.server.library.util.StringUtil;
import org.apache.skywalking.oap.server.library.server.ssl.AbstractSslContext;
import org.apache.skywalking.oap.server.library.server.ssl.PrivateKeyUtil;

/**
 * Load SslContext dynamically.
 */
public class DynamicSslContext extends AbstractSslContext {

    public static DynamicSslContext forServer(final String privateKeyFile,
                                              final String certChainFile,
                                              final String trustedCAsFile) {
        return new DynamicSslContext(privateKeyFile, certChainFile, trustedCAsFile);
    }

    public static DynamicSslContext forClient(final String caFile) {
        return new DynamicSslContext(caFile);
    }

    protected DynamicSslContext(String privateKeyFile, String certChainFile, String trustedCAsFile) {
        super(privateKeyFile, certChainFile, trustedCAsFile);
    }

    protected DynamicSslContext(String caFile) {
        super(caFile);
    }

    @Override
    protected void updateContext(String caFile) {
        try {
            setCtx(GrpcSslContexts.forClient().trustManager(Paths.get(caFile).toFile()).build());
        } catch (SSLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    protected void updateContext(final String privateKeyFile, final String certChainFile, final String trustedCAsFile) {
        try (InputStream cert = new FileInputStream(Paths.get(certChainFile).toFile());
             InputStream key = PrivateKeyUtil.loadDecryptionKey(privateKeyFile)) {

            SslContextBuilder builder = GrpcSslContexts.configure(
                SslContextBuilder.forServer(cert, key),
                SslProvider.OPENSSL
            );

            if (StringUtil.isNotEmpty(trustedCAsFile)) {
                builder.trustManager(Paths.get(trustedCAsFile).toFile())
                    .clientAuth(ClientAuth.REQUIRE);
            }

            setCtx(builder.build());
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

}
