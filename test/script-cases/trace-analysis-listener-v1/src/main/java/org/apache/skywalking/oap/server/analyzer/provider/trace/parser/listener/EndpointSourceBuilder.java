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

package org.apache.skywalking.oap.server.analyzer.provider.trace.parser.listener;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.apache.skywalking.apm.network.common.v3.KeyStringValuePair;
import org.apache.skywalking.oap.server.core.analysis.Layer;
import org.apache.skywalking.oap.server.core.config.NamingControl;
import org.apache.skywalking.oap.server.core.source.DetectPoint;
import org.apache.skywalking.oap.server.core.source.Endpoint;
import org.apache.skywalking.oap.server.core.source.RequestType;

/**
 * Endpoint traffic source builder. Endpoint represents an entrance to expose logic.
 * Typically, it could be HTTP URI, gRPC service name, etc. for RPC, or a local method is required to be analyzed.
 *
 * @since 9.0.0
 */
@RequiredArgsConstructor
class EndpointSourceBuilder {
    protected final NamingControl namingControl;

    @Getter
    @Setter
    protected long timeBucket;
    @Getter
    @Setter
    protected String destServiceName;
    @Getter
    @Setter
    protected Layer destLayer;
    @Getter
    @Setter
    protected String destServiceInstanceName;
    @Getter
    @Setter
    protected String destEndpointName;
    @Getter
    @Setter
    protected int latency;
    @Getter
    @Setter
    protected boolean status;
    @Getter
    @Setter
    protected int httpResponseStatusCode;
    @Getter
    @Setter
    protected String rpcStatusCode;
    @Getter
    @Setter
    protected RequestType type;
    @Getter
    @Setter
    protected DetectPoint detectPoint;
    @Getter
    protected final List<String> tags = new ArrayList<>();
    @Getter
    protected final Map<String, String> originalTags = new HashMap<>();

    void prepare() {
        this.destServiceName = namingControl.formatServiceName(destServiceName);
        this.destServiceInstanceName = namingControl.formatInstanceName(destServiceInstanceName);
        this.destEndpointName = namingControl.formatEndpointName(destServiceName, destEndpointName);
    }

    /**
     * Endpoint meta and metrics of {@link #destEndpointName} related source. The metrics base on the OAL scripts.
     */
    Endpoint toEndpoint() {
        Endpoint endpoint = new Endpoint();
        endpoint.setName(destEndpointName);
        endpoint.setServiceName(destServiceName);
        endpoint.setServiceLayer(destLayer);
        endpoint.setServiceInstanceName(destServiceInstanceName);
        endpoint.setLatency(latency);
        endpoint.setStatus(status);
        endpoint.setHttpResponseStatusCode(httpResponseStatusCode);
        endpoint.setRpcStatusCode(rpcStatusCode);
        endpoint.setType(type);
        endpoint.setTags(tags);
        endpoint.setOriginalTags(originalTags);
        endpoint.setTimeBucket(timeBucket);
        return endpoint;
    }

    void setTag(KeyStringValuePair tag) {
        tags.add(tag.getKey().trim() + ":" + tag.getValue().trim());
        originalTags.put(tag.getKey(), tag.getValue());
    }
}
