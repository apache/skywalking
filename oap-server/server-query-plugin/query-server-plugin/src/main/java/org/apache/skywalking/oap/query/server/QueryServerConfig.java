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

package org.apache.skywalking.oap.query.server;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
@Setter
public class QueryServerConfig extends ModuleConfig {
    private String restHost;
    /**
     * A port above 0 starts the query server; 0 keeps the query APIs on the core REST server.
     */
    private int restPort;
    private String restContextPath;
    private long restIdleTimeOut = 30000;
    private int restAcceptQueueSize = 0;
    private int httpMaxRequestHeaderSize = 8192;
    /**
     * Server-side TLS of the query server. The certificate and key are read from disk and reloaded on rotation
     * without a restart.
     */
    private boolean restSSLEnabled = false;
    private String restSSLKeyPath;
    private String restSSLCertChainPath;
}
