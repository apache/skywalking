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
public abstract class CiliumMetrics extends Source {
    public static final String VERDICT_FORWARDED = "forwarded";
    public static final String VERDICT_DROPPED = "dropped";

    public static final String TYPE_TCP = "tcp";
    public static final String TYPE_HTTP = "http";
    public static final String TYPE_DNS = "dns";
    public static final String TYPE_KAFKA = "kafka";

    public static final String DIRECTION_INGRESS = "ingress";
    public static final String DIRECTION_EGRESS = "egress";

    // Basic information
    private String verdict;
    private String type;
    private String direction;

    // For Dropped Package Reason
    private String dropReason;

    // For L7 metrics
    private HTTPMetrics http;
    private KafkaMetrics kafka;
    private DNSMetrics dns;
    private long duration;
    private boolean success;

    @Data
    public static class HTTPMetrics {
        private String url;
        private int code;
        private String protocol;
        private String method;
    }

    @Data
    public static class KafkaMetrics {
        private int errorCode;
        private String errorCodeString;
        private int apiVersion;
        private String apiKey;
        private int correlationId;
        private String topic;
    }

    @Data
    public static class DNSMetrics {
        private String domain;
        private String queryType;
        private int rcode;
        private String rcodeString;
        private int ttl;
        private int ipCount;
    }
}
