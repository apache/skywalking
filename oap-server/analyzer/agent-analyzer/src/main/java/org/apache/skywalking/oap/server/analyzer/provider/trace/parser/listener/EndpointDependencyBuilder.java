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

import lombok.RequiredArgsConstructor;
import org.apache.skywalking.oap.server.core.source.Endpoint;
import org.apache.skywalking.oap.server.core.source.EndpointMeta;
import org.apache.skywalking.oap.server.core.source.EndpointRelation;

/**
 * EndpointDependencyBuilder is a wrapper of {@link RPCTrafficSourceBuilder}, and only exposes to build endpoint
 * relation relative sources.
 *
 * @since 9.0.0
 */
@RequiredArgsConstructor
public class EndpointDependencyBuilder {
    private final RPCTrafficSourceBuilder rpcTrafficSourceBuilder;

    void prepare() {
        rpcTrafficSourceBuilder.prepare();
    }

    EndpointRelation toEndpointRelation() {
        return rpcTrafficSourceBuilder.toEndpointRelation();
    }

    Endpoint toEndpoint() {
        return rpcTrafficSourceBuilder.toEndpoint();
    }

    EndpointMeta toSourceEndpoint() {
        EndpointMeta sourceEndpoint = new EndpointMeta();
        sourceEndpoint.setServiceName(rpcTrafficSourceBuilder.getSourceServiceName());
        sourceEndpoint.setServiceNormal(rpcTrafficSourceBuilder.getSourceLayer().isNormal());
        sourceEndpoint.setEndpoint(rpcTrafficSourceBuilder.getSourceEndpointName());
        sourceEndpoint.setTimeBucket(rpcTrafficSourceBuilder.getTimeBucket());
        return sourceEndpoint;
    }
}
