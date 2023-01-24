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

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class HTTPServerConfig {

    private String host;
    private int port;
    private String contextPath;

    @Builder.Default
    private int maxThreads = 200;
    @Builder.Default
    private long idleTimeOut = 30000;
    @Builder.Default
    private int acceptQueueSize = 0;
    @Builder.Default
    private int maxRequestHeaderSize = 8192;

    @Builder.Default
    private boolean enableTLS = false;

    private String tlsKeyPath;
    private String tlsCertChainPath;
}
