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

package org.apache.skywalking.oap.server.receiver.sharing.server;

import lombok.Getter;
import lombok.Setter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
@Setter
public class SharingServerConfig extends ModuleConfig {
    private String restHost;
    /**
     * Only setting the real port(not 0) makes the jetty server online.
     */
    private int restPort;
    private String restContextPath;
    private int restMinThreads = 1;
    private int restMaxThreads = 200;
    private long restIdleTimeOut = 30000;
    private int restAcceptorPriorityDelta = 0;
    private int restAcceptQueueSize = 0;

    private String gRPCHost;
    /**
     * Only setting the real port(not 0) makes the gRPC server online.
     */
    private int gRPCPort;
    private int maxConcurrentCallsPerConnection;
    private int maxMessageSize;
    private int gRPCThreadPoolSize;
    private int gRPCThreadPoolQueueSize;
    private String authentication;
    private boolean gRPCSslEnabled = false;
    private String gRPCSslKeyPath;
    private String gRPCSslCertChainPath;
}
