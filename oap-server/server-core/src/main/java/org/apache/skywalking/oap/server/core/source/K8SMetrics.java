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

package org.apache.skywalking.oap.server.core.source;

import lombok.Data;

@Data
public abstract class K8SMetrics extends Source {
    public static final String TYPE_CONNECT = "connect";
    public static final String TYPE_ACCEPT = "accept";
    public static final String TYPE_CLOSE = "close";
    public static final String TYPE_WRITE = "write";
    public static final String TYPE_READ = "read";
    public static final String TYPE_PROTOCOL = "protocol";

    public static final String PROTOCOL_TYPE_HTTP = "http";

    // related metrics
    private String type;

    private Connect connect;
    private Accept accept;
    private Close close;
    private Write write;
    private Read read;
    private Protocol protocol;

    @Data
    public static class Connect {
        private long duration;
        private boolean success;
    }

    @Data
    public static class Accept {
        private long duration;
    }

    @Data
    public static class Close {
        private long duration;
        private boolean success;
    }

    @Data
    public static class Write {
        private long duration;
        private String syscall;

        private WriteL4 l4;
        private WriteL3 l3;
        private WriteL2 l2;
    }

    @Data
    public static class WriteL4 {
        private long duration;
        private long transmitPackageCount;
        private long retransmitPackageCount;
        private long totalPackageSize;
    }

    @Data
    public static class WriteL3 {
        private long duration;
        private long localDuration;
        private long outputDuration;
        private long resolveMACCount;
        private long resolveMACDuration;
        private long netFilterCount;
        private long netFilterDuration;
    }

    @Data
    public static class WriteL2 {
        private long duration;
        private String networkDeviceName;
        private long enterQueueBufferCount;
        private long readySendDuration;
        private long networkDeviceSendDuration;
    }

    @Data
    public static class Read {
        private long duration;
        private String syscall;

        private ReadL4 l4;
        private ReadL3 l3;
        private ReadL2 l2;
    }

    @Data
    public static class ReadL4 {
        private long duration;
    }

    @Data
    public static class ReadL3 {
        private long duration;
        private long rcvDuration;
        private long localDuration;
        private long netFilterCount;
        private long netFilterDuration;
    }

    @Data
    public static class ReadL2 {
        private String netDeviceName;
        private long packageCount;
        private long totalPackageSize;
        private long packageToQueueDuration;
        private long rcvPackageFromQueueDuration;
    }

    @Data
    public static class Protocol {
        private String type;

        private ProtocolHTTP http;
    }

    @Data
    public static class ProtocolHTTP {
        private long latency;
        private String url;
        private String method;
        private int statusCode;
        private long sizeOfRequestHeader;
        private long sizeOfRequestBody;
        private long sizeOfResponseHeader;
        private long sizeOfResponseBody;
    }

    @Data
    public static abstract class ProtocolMetrics extends Source {
        private String type;

        private ProtocolHTTP http;
    }

}
