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

package org.apache.skywalking.oap.server.library.server.ssl;

import io.netty.handler.ssl.SslContextBuilder;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Paths;
import javax.net.ssl.SSLException;

public class HTTPDynamicSslContext extends AbstractSslContext {

    public static HTTPDynamicSslContext forServer(String privateKeyFile, String certChainFile) {
        return new HTTPDynamicSslContext(privateKeyFile, certChainFile);
    }

    public static HTTPDynamicSslContext forClient(String caFile) {
        return new HTTPDynamicSslContext(caFile);
    }

    protected HTTPDynamicSslContext(String privateKeyFile, String certChainFile) {
        super(privateKeyFile, certChainFile, null);
    }

    protected HTTPDynamicSslContext(String caFile) {
        super(caFile);
    }

    @Override
    protected void updateContext(String caFile) {
        try {
            setCtx(SslContextBuilder.forClient().trustManager(Paths.get(caFile).toFile()).build());
        } catch (SSLException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    protected void updateContext(final String privateKeyFile, final String certChainFile, final String trustedCAsFile) {
        try (InputStream cert = new FileInputStream(Paths.get(certChainFile).toFile());
             InputStream key = PrivateKeyUtil.loadDecryptionKey(privateKeyFile)) {

            setCtx(SslContextBuilder.forServer(cert, key).build());
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
