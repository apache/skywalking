/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.skywalking.oap.server.receiver.aws.firehose;

import lombok.Getter;
import org.apache.skywalking.oap.server.library.module.ModuleConfig;

@Getter
public class AWSFirehoseReceiverModuleConfig extends ModuleConfig {
    private String host;
    private int port;
    private String contextPath;
    private int maxThreads = 200;
    private long idleTimeOut = 30000;
    private int acceptQueueSize = 0;
    private int maxRequestHeaderSize = 8192;
    private String firehoseAccessKey;
    private boolean enableTLS = false;
    private String tlsKeyPath;
    private String tlsCertChainPath;
}
