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
import io.netty.buffer.ByteBufAllocator;
import io.netty.handler.ssl.ApplicationProtocolNegotiator;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import io.netty.handler.ssl.SslProvider;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.util.List;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSessionContext;
import org.apache.skywalking.oap.server.library.util.MultipleFilesChangeMonitor;

/**
 * Load SslContext dynamically.
 */
public class DynamicSslContext extends SslContext {
    private final MultipleFilesChangeMonitor monitor;
    private volatile SslContext ctx;

    public static DynamicSslContext forServer(final String privateKeyFile, final String certChainFile) {
        return new DynamicSslContext(privateKeyFile, certChainFile);
    }

    public static DynamicSslContext forClient(final String caFile) {
        return new DynamicSslContext(caFile);
    }

    private DynamicSslContext(final String privateKeyFile, final String certChainFile) {
        updateContext(privateKeyFile, certChainFile);
        monitor = new MultipleFilesChangeMonitor(
            10,
            readableContents -> updateContext(privateKeyFile, certChainFile),
            certChainFile,
            privateKeyFile);
    }

    private DynamicSslContext(final String caFile) {
        updateContext(caFile);
        monitor = new MultipleFilesChangeMonitor(
            10,
            readableContents -> updateContext(caFile),
            caFile);
    }

    private void updateContext(String caFile) {
        try {
            ctx = GrpcSslContexts.forClient().trustManager(Paths.get(caFile).toFile()).build();
        } catch (SSLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private void updateContext(final String privateKeyFile, final String certChainFile) {
        try {
            ctx = GrpcSslContexts
                .configure(SslContextBuilder
                    .forServer(
                        new FileInputStream(Paths.get(certChainFile).toFile()),
                        PrivateKeyUtil.loadDecryptionKey(privateKeyFile)),
                    SslProvider.OPENSSL)
                .build();
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public void start() {
       monitor.start();
    }

    @Override
    public final boolean isClient() {
        return ctx.isClient();
    }

    @Override
    public final List<String> cipherSuites() {
        return ctx.cipherSuites();
    }

    @Override
    public final long sessionCacheSize() {
        return ctx.sessionCacheSize();
    }

    @Override
    public final long sessionTimeout() {
        return ctx.sessionTimeout();
    }

    @Override
    public final ApplicationProtocolNegotiator applicationProtocolNegotiator() {
        return ctx.applicationProtocolNegotiator();
    }

    @Override
    public final SSLEngine newEngine(ByteBufAllocator alloc) {
        return ctx.newEngine(alloc);
    }

    @Override
    public final SSLEngine newEngine(ByteBufAllocator alloc, String peerHost, int peerPort) {
        return ctx.newEngine(alloc, peerHost, peerPort);
    }

    @Override
    public final SSLSessionContext sessionContext() {
        return ctx.sessionContext();
    }
}
